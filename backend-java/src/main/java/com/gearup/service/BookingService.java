package com.gearup.service;

import com.gearup.auth.CustomerUser;
import com.gearup.auth.StaffUser;
import com.gearup.db.Database;
import com.gearup.exception.BadRequestException;
import com.gearup.exception.ConflictException;
import com.gearup.exception.NotFoundException;
import com.gearup.model.BookingConfirmation;
import com.gearup.model.BookingRequest;
import com.gearup.model.CarStatus;
import com.gearup.model.CustomerReservation;
import com.gearup.model.LabeledEnum;
import com.gearup.model.PaymentStatus;
import com.gearup.model.ReservationStatus;
import com.gearup.model.ReservationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Creating, listing and changing car bookings (the {@code reservations} table).
 *
 * <p><b>Concurrency.</b> Up to ten requests run at the same time (one per worker thread), and
 * several copies of the app may run against the same database. Two customers could therefore
 * try to book the same car for the same dates at the same moment. {@link #book} prevents a
 * double booking by locking the car's row with {@code SELECT ... FOR UPDATE} before checking
 * for overlaps: the second transaction waits until the first commits, then sees its booking.
 * The lock lives in the database, not in Java memory, so it works across processes (Factor VI).
 */
public final class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final Database database;

    public BookingService(Database database) {
        this.database = database;
    }

    /** The booked car's price and status, read while holding the row lock. */
    private record LockedCar(BigDecimal pricePerDay, CarStatus status) {
    }

    /** An Active reservation whose end date has passed. */
    private record ExpiredBooking(String resId, String carId, PaymentStatus paymentStatus) {
    }

    /**
     * Books a car for the logged-in customer and creates its unpaid invoice.
     *
     * @throws BadRequestException if the dates are missing, in the past, or in the wrong order
     * @throws NotFoundException   if the car does not exist
     * @throws ConflictException   if the car is in maintenance or already booked for those dates
     */
    public BookingConfirmation book(CustomerUser customer, BookingRequest request) {
        String carId = Validation.requireText(request.carId(), "car_id");
        LocalDate start = Validation.requireValue(request.startDate(), "start_date");
        LocalDate end = Validation.requireValue(request.endDate(), "end_date");
        if (start.isBefore(LocalDate.now())) {
            throw new BadRequestException("start_date cannot be in the past");
        }
        if (!end.isAfter(start)) {
            throw new BadRequestException("end_date must be after start_date");
        }
        long days = ChronoUnit.DAYS.between(start, end);

        BookingConfirmation confirmation = database.inTransaction(tx -> {
            // 1. Lock the car. Any other booking for this car now waits here until we commit.
            LockedCar car = tx.queryOne(
                            "SELECT price_per_day, status FROM public.cars WHERE car_id = ? FOR UPDATE",
                            row -> new LockedCar(row.getBigDecimal("price_per_day"),
                                    LabeledEnum.fromLabel(CarStatus.class, row.getString("status"))),
                            carId)
                    .orElseThrow(() -> new NotFoundException("Car not found"));
            if (car.status() == CarStatus.MAINTENANCE) {
                throw new ConflictException("Car is under maintenance and cannot be booked");
            }

            // 2. With the lock held, no one else can add a booking for this car, so this check is reliable.
            boolean overlaps = tx.queryOne("""
                            SELECT 1 FROM public.reservations
                            WHERE car_id = ?
                              AND status IN ('Reserved', 'Active')
                              AND NOT (? <= start_date OR ? >= end_date)
                            LIMIT 1""",
                    row -> true, carId, end, start).isPresent();
            if (overlaps) {
                throw new ConflictException("Car not available for selected dates");
            }

            // 3. Create the reservation and its invoice.
            BigDecimal total = car.pricePerDay().multiply(BigDecimal.valueOf(days));
            String reservationId = tx.queryOne("""
                            INSERT INTO public.reservations (customer_license_no, car_id, start_date, end_date, status)
                            VALUES (?, ?, ?, ?, 'Reserved')
                            RETURNING res_id""",
                    row -> row.getString("res_id"),
                    customer.licenseNo(), carId, start, end).orElseThrow();
            String invoiceId = tx.queryOne("""
                            INSERT INTO public.invoices (reservation_id, total_amount, payment_status)
                            VALUES (?, ?, 'unpaid')
                            RETURNING inv_id""",
                    row -> row.getString("inv_id"),
                    reservationId, total).orElseThrow();

            // 4. Mark the car as reserved, but never overwrite "Rented" for a car that is out on another booking.
            tx.update("UPDATE public.cars SET status = 'Reserved' WHERE car_id = ? AND status = 'Available'", carId);

            return new BookingConfirmation(reservationId, invoiceId, total);
        });

        log.info("Booking {} confirmed for car {}", confirmation.reservationId(), carId);
        return confirmation;
    }

    /** Every reservation with its customer, car and invoice, newest start date first (staff view). */
    public List<ReservationSummary> listAll() {
        return database.inTransaction(tx -> tx.queryList("""
                SELECT r.res_id, r.customer_license_no, r.car_id, r.start_date, r.end_date, r.status,
                       c.first_name || ' ' || c.last_name AS customer_name,
                       c.email AS customer_email,
                       car.brand || ' ' || car.model AS car_name,
                       inv.inv_id, inv.total_amount, inv.payment_status
                FROM public.reservations r
                LEFT JOIN public.customers c ON r.customer_license_no = c.license_no
                LEFT JOIN public.cars car ON r.car_id = car.car_id
                LEFT JOIN public.invoices inv ON r.res_id = inv.reservation_id
                ORDER BY r.start_date DESC""",
                BookingService::toSummary));
    }

    /** The logged-in customer's own reservations, newest start date first. */
    public List<CustomerReservation> listFor(CustomerUser customer) {
        return database.inTransaction(tx -> tx.queryList("""
                        SELECT r.res_id, r.car_id, r.start_date, r.end_date, r.status,
                               car.brand || ' ' || car.model || ' ' || car.year AS car_name,
                               car.photo_url, inv.total_amount, inv.payment_status
                        FROM public.reservations r
                        LEFT JOIN public.cars car ON r.car_id = car.car_id
                        LEFT JOIN public.invoices inv ON r.res_id = inv.reservation_id
                        WHERE r.customer_license_no = ?
                        ORDER BY r.start_date DESC""",
                BookingService::toCustomerReservation, customer.licenseNo()));
    }

    /**
     * Staff change a reservation's status by hand; the car's status follows
     * (see {@link ReservationStatus#carStatus()}).
     *
     * @throws NotFoundException if the reservation does not exist
     */
    public void updateStatus(String reservationId, ReservationStatus newStatus, StaffUser staff) {
        database.inTransaction(tx -> {
            String carId = tx.queryOne(
                            "SELECT car_id FROM public.reservations WHERE res_id = ? FOR UPDATE",
                            row -> row.getString("car_id"), reservationId)
                    .orElseThrow(() -> new NotFoundException("Reservation not found"));

            tx.update("UPDATE public.reservations SET status = ?::reservation_status WHERE res_id = ?",
                    newStatus, reservationId);
            tx.update("UPDATE public.cars SET status = ?::car_status WHERE car_id = ?",
                    newStatus.carStatus(), carId);
            return null;
        });
        log.info("Reservation {} set to {} by {}", reservationId, newStatus.label(), staff.empId());
    }

    /**
     * Closes Active reservations whose end date has passed: Completed if the invoice is paid,
     * otherwise Cancelled. Their cars become Available again.
     *
     * @return how many reservations were closed
     */
    public int closeExpired(StaffUser staff) {
        int closed = database.inTransaction(tx -> {
            // FOR UPDATE OF r: if two staff members press the button at once, the second waits
            // and then finds nothing left to close, instead of closing the same rows twice.
            List<ExpiredBooking> expired = tx.queryList("""
                            SELECT r.res_id, r.car_id, COALESCE(inv.payment_status, 'unpaid') AS payment_status
                            FROM public.reservations r
                            LEFT JOIN public.invoices inv ON r.res_id = inv.reservation_id
                            WHERE r.status = 'Active' AND r.end_date < CURRENT_DATE
                            FOR UPDATE OF r""",
                    row -> new ExpiredBooking(row.getString("res_id"), row.getString("car_id"),
                            LabeledEnum.fromLabel(PaymentStatus.class, row.getString("payment_status"))));

            for (ExpiredBooking booking : expired) {
                ReservationStatus newStatus = booking.paymentStatus() == PaymentStatus.PAID
                        ? ReservationStatus.COMPLETED
                        : ReservationStatus.CANCELLED;
                tx.update("UPDATE public.reservations SET status = ?::reservation_status WHERE res_id = ?",
                        newStatus, booking.resId());
                tx.update("UPDATE public.cars SET status = 'Available' WHERE car_id = ?", booking.carId());
                log.info("Expired reservation {} closed as {}", booking.resId(), newStatus.label());
            }
            return expired.size();
        });
        log.info("{} closed {} expired reservation(s)", staff.empId(), closed);
        return closed;
    }

    private static ReservationSummary toSummary(ResultSet row) throws SQLException {
        return new ReservationSummary(
                row.getString("res_id"),
                row.getString("customer_license_no"),
                row.getString("car_id"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class),
                LabeledEnum.fromLabel(ReservationStatus.class, row.getString("status")),
                row.getString("customer_name"),
                row.getString("customer_email"),
                row.getString("car_name"),
                row.getString("inv_id"),
                row.getBigDecimal("total_amount"),
                LabeledEnum.fromNullableLabel(PaymentStatus.class, row.getString("payment_status")));
    }

    private static CustomerReservation toCustomerReservation(ResultSet row) throws SQLException {
        return new CustomerReservation(
                row.getString("res_id"),
                row.getString("car_id"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class),
                LabeledEnum.fromLabel(ReservationStatus.class, row.getString("status")),
                row.getString("car_name"),
                row.getString("photo_url"),
                row.getBigDecimal("total_amount"),
                LabeledEnum.fromNullableLabel(PaymentStatus.class, row.getString("payment_status")));
    }
}
