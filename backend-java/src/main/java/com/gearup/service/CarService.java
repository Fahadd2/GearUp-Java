package com.gearup.service;

import com.gearup.db.Database;
import com.gearup.exception.BadRequestException;
import com.gearup.exception.NotFoundException;
import com.gearup.model.Car;
import com.gearup.model.CarCategory;
import com.gearup.model.CarFilter;
import com.gearup.model.CarStatus;
import com.gearup.model.CarUpdate;
import com.gearup.model.LabeledEnum;
import com.gearup.model.Transmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Searching and editing the car fleet.
 *
 * <p>Queries are built from fixed SQL fragments; every user-supplied value is passed as a
 * {@code ?} parameter, never pasted into the SQL text.
 */
public final class CarService {

    private static final Logger log = LoggerFactory.getLogger(CarService.class);

    private static final String SELECT_CARS = """
            SELECT car_id, plate_no, brand, model, year, category, fuel_type, color, seats,
                   transmission, price_per_day, status, COALESCE(photo_url, '') AS photo_url, created_at
            FROM public.cars
            """;

    private static final int MIN_YEAR = 1980;

    private final Database database;

    public CarService(Database database) {
        this.database = database;
    }

    /** Cars matching the filter, cheapest first. With a date range, cars already booked for it are left out. */
    public List<Car> findCars(CarFilter filter) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (filter.category() != null) {
            conditions.add("category = ?::car_category");
            params.add(filter.category());
        }
        if (filter.minSeats() != null) {
            conditions.add("seats >= ?");
            params.add(filter.minSeats());
        }
        if (filter.transmission() != null) {
            conditions.add("transmission = ?::transmission_type");
            params.add(filter.transmission());
        }
        if (filter.minPrice() != null) {
            conditions.add("price_per_day >= ?");
            params.add(filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            conditions.add("price_per_day <= ?");
            params.add(filter.maxPrice());
        }
        if (filter.startDate() != null && filter.endDate() != null) {
            // Two date ranges overlap unless one ends before the other starts.
            conditions.add("""
                    car_id NOT IN (
                        SELECT car_id FROM public.reservations
                        WHERE status IN ('Reserved', 'Active')
                          AND NOT (? <= start_date OR ? >= end_date))""");
            params.add(filter.endDate());
            params.add(filter.startDate());
        }

        String where = conditions.isEmpty() ? "" : "WHERE " + String.join("\n  AND ", conditions) + "\n";
        String sql = SELECT_CARS + where + "ORDER BY price_per_day, brand, model";

        return database.inTransaction(tx -> tx.queryList(sql, CarService::toCar, params.toArray()));
    }

    /**
     * Changes the given fields of one car (staff only; the route checks the token).
     *
     * @throws BadRequestException if no field is given or a value is out of range
     * @throws NotFoundException   if there is no car with that id
     */
    public void updateCar(String carId, CarUpdate update) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (update.brand() != null) {
            assignments.add("brand = ?");
            params.add(update.brand());
        }
        if (update.model() != null) {
            assignments.add("model = ?");
            params.add(update.model());
        }
        if (update.year() != null) {
            if (update.year() < MIN_YEAR) {
                throw new BadRequestException("year must be " + MIN_YEAR + " or later");
            }
            assignments.add("year = ?");
            params.add(update.year());
        }
        if (update.category() != null) {
            assignments.add("category = ?::car_category");
            params.add(update.category());
        }
        if (update.transmission() != null) {
            assignments.add("transmission = ?::transmission_type");
            params.add(update.transmission());
        }
        if (update.pricePerDay() != null) {
            if (update.pricePerDay().signum() <= 0) {
                throw new BadRequestException("price_per_day must be greater than 0");
            }
            assignments.add("price_per_day = ?");
            params.add(update.pricePerDay());
        }
        if (update.status() != null) {
            assignments.add("status = ?::car_status");
            params.add(update.status());
        }
        if (assignments.isEmpty()) {
            throw new BadRequestException("No fields to update");
        }

        String sql = "UPDATE public.cars SET " + String.join(", ", assignments) + " WHERE car_id = ?";
        params.add(carId);

        int updated = database.inTransaction(tx -> tx.update(sql, params.toArray()));
        if (updated == 0) {
            throw new NotFoundException("Car not found");
        }
        log.info("Car {} updated: {}", carId, update);
    }

    private static Car toCar(ResultSet row) throws SQLException {
        return new Car(
                row.getString("car_id"),
                row.getString("plate_no"),
                row.getString("brand"),
                row.getString("model"),
                row.getInt("year"),
                LabeledEnum.fromLabel(CarCategory.class, row.getString("category")),
                row.getString("fuel_type"),
                row.getString("color"),
                row.getInt("seats"),
                LabeledEnum.fromLabel(Transmission.class, row.getString("transmission")),
                row.getObject("price_per_day", BigDecimal.class),
                LabeledEnum.fromLabel(CarStatus.class, row.getString("status")),
                row.getString("photo_url"),
                row.getObject("created_at", OffsetDateTime.class));
    }
}
