package com.gearup.service;

import com.gearup.auth.StaffUser;
import com.gearup.db.Database;
import com.gearup.db.Transaction;
import com.gearup.exception.BadRequestException;
import com.gearup.exception.NotFoundException;
import com.gearup.model.CarStatus;
import com.gearup.model.LabeledEnum;
import com.gearup.model.PaymentStatus;
import com.gearup.model.RentalCloseRequest;
import com.gearup.model.RentalStartRequest;
import com.gearup.model.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Car pick-up (start of a rental) and return (close of a rental, with the final bill).
 *
 * <p>Both operations lock the reservation and its car with {@code SELECT ... FOR UPDATE}, so a
 * rental cannot be started or closed twice by two staff members at the same moment.
 */
public final class RentalService {

    private static final Logger log = LoggerFactory.getLogger(RentalService.class);
    private static final Pattern RESERVATION_ID = Pattern.compile("RES-\\d+");

    private final Database database;

    public RentalService(Database database) {
        this.database = database;
    }

    /** A reservation joined with its car, read while both rows are locked. */
    private record LockedRental(String carId, ReservationStatus status, CarStatus carStatus,
                                LocalDate startDate, LocalDate endDate, BigDecimal pricePerDay) {
    }

    /**
     * The customer picks up the car: reservation Reserved → Active, car → Rented.
     *
     * @throws NotFoundException   if the reservation does not exist
     * @throws BadRequestException if the reservation is not Reserved or the car is not available
     */
    public void start(RentalStartRequest request, StaffUser staff) {
        String reservationId = Validation.requireFormat(request.reservationId(), "reservation_id",
                RESERVATION_ID, "RES-3");

        database.inTransaction(tx -> {
            LockedRental rental = lockRental(tx, reservationId);
            if (rental.status() != ReservationStatus.RESERVED) {
                throw new BadRequestException("Reservation is not in 'Reserved' state");
            }
            if (rental.carStatus() != CarStatus.AVAILABLE && rental.carStatus() != CarStatus.RESERVED) {
                throw new BadRequestException("Car is not available (status=" + rental.carStatus().label() + ")");
            }

            tx.update("UPDATE public.reservations SET status = 'Active' WHERE res_id = ?", reservationId);
            tx.update("UPDATE public.cars SET status = 'Rented' WHERE car_id = ?", rental.carId());

            // Bookings made through the API always have an invoice; older data might not.
            tx.update("""
                    INSERT INTO public.invoices (reservation_id, total_amount, payment_status)
                    SELECT ?, 0, 'unpaid'::payment_status
                    WHERE NOT EXISTS (SELECT 1 FROM public.invoices WHERE reservation_id = ?)""",
                    reservationId, reservationId);
            return null;
        });
        log.info("Rental {} started by {}", reservationId, staff.empId());
    }

    /**
     * The car is returned: reservation Active → Completed, car → Available, and the invoice total
     * becomes days × price per day + damage fee + refuel fee.
     *
     * @return the final invoice total
     * @throws NotFoundException   if the reservation does not exist
     * @throws BadRequestException if the reservation is not Active or a fee is negative
     */
    public BigDecimal close(RentalCloseRequest request, StaffUser staff) {
        String reservationId = Validation.requireFormat(request.reservationId(), "reservation_id",
                RESERVATION_ID, "RES-3");
        BigDecimal damageFee = Validation.nonNegativeOrZero(request.damageFee(), "damage_fee");
        BigDecimal refuelFee = Validation.nonNegativeOrZero(request.refuelFee(), "refuel_fee");

        BigDecimal total = database.inTransaction(tx -> {
            LockedRental rental = lockRental(tx, reservationId);
            if (rental.status() != ReservationStatus.ACTIVE) {
                throw new BadRequestException("Reservation is not in 'Active' state");
            }

            long days = Math.max(ChronoUnit.DAYS.between(rental.startDate(), rental.endDate()), 1);
            BigDecimal finalTotal = rental.pricePerDay()
                    .multiply(BigDecimal.valueOf(days))
                    .add(damageFee)
                    .add(refuelFee);

            tx.update("UPDATE public.reservations SET status = 'Completed' WHERE res_id = ?", reservationId);
            tx.update("UPDATE public.cars SET status = 'Available' WHERE car_id = ?", rental.carId());
            saveFinalInvoice(tx, reservationId, finalTotal);
            return finalTotal;
        });
        log.info("Rental {} closed by {} with total {} SAR", reservationId, staff.empId(), total);
        return total;
    }

    private static LockedRental lockRental(Transaction tx, String reservationId) throws SQLException {
        return tx.queryOne("""
                        SELECT r.car_id, r.status, c.status AS car_status,
                               r.start_date, r.end_date, c.price_per_day
                        FROM public.reservations r
                        JOIN public.cars c ON c.car_id = r.car_id
                        WHERE r.res_id = ?
                        FOR UPDATE""",
                        row -> new LockedRental(
                                row.getString("car_id"),
                                LabeledEnum.fromLabel(ReservationStatus.class, row.getString("status")),
                                LabeledEnum.fromLabel(CarStatus.class, row.getString("car_status")),
                                row.getObject("start_date", LocalDate.class),
                                row.getObject("end_date", LocalDate.class),
                                row.getBigDecimal("price_per_day")),
                        reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
    }

    /** Sets the invoice to its final total; its payment status is recomputed from the payments already made. */
    private static void saveFinalInvoice(Transaction tx, String reservationId, BigDecimal total) throws SQLException {
        Optional<String> invoiceId = tx.queryOne(
                "SELECT inv_id FROM public.invoices WHERE reservation_id = ? FOR UPDATE",
                row -> row.getString("inv_id"), reservationId);

        if (invoiceId.isEmpty()) {
            tx.update("INSERT INTO public.invoices (reservation_id, total_amount, payment_status) VALUES (?, ?, 'unpaid')",
                    reservationId, total);
            return;
        }
        BigDecimal paid = tx.queryOne(
                "SELECT COALESCE(SUM(amount), 0) AS paid FROM public.payments WHERE invoice_id = ?",
                row -> row.getBigDecimal("paid"), invoiceId.get()).orElseThrow();
        tx.update("UPDATE public.invoices SET total_amount = ?, payment_status = ?::payment_status WHERE inv_id = ?",
                total, PaymentStatus.forAmounts(paid, total), invoiceId.get());
    }
}
