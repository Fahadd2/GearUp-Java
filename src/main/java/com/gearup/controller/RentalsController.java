package com.gearup.controller;

import com.gearup.dto.CloseRentalRequest;
import com.gearup.dto.StartRentalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/rentals")
public class RentalsController {

    private final JdbcTemplate jdbcTemplate;

    public RentalsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startRental(@Valid @RequestBody StartRentalRequest request) {
        String rid = request.getReservationId();

        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT r.res_id, r.status, r.car_id, c.status AS car_status
            FROM public.reservations r
            JOIN public.cars c ON c.car_id = r.car_id
            WHERE r.res_id = ?
        """, rid);

        if (!"Reserved".equals(row.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Reservation is not in 'Reserved' state");
        }

        String carStatus = (String) row.get("car_status");
        if (!("Available".equals(carStatus) || "Reserved".equals(carStatus))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Car is not available (status=" + carStatus + ")");
        }

        jdbcTemplate.update("UPDATE public.reservations SET status='Active' WHERE res_id=?", rid);
        jdbcTemplate.update("UPDATE public.cars SET status='Rented' WHERE car_id=?", row.get("car_id"));

        // Ensure invoice exists
        Integer invCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.invoices WHERE reservation_id=?",
            Integer.class, rid
        );
        if (invCount == null || invCount == 0) {
            jdbcTemplate.update("""
                INSERT INTO public.invoices (reservation_id, issue_date, total_amount, payment_status)
                VALUES (?, CURRENT_DATE, 0, 'unpaid')
            """, rid);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("message", "Rental started");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/close")
    public ResponseEntity<?> closeRental(@Valid @RequestBody CloseRentalRequest request) {
        String rid = request.getReservationId();
        BigDecimal damageFee = request.getDamageFee() != null ? request.getDamageFee() : BigDecimal.ZERO;
        BigDecimal refuelFee = request.getRefuelFee() != null ? request.getRefuelFee() : BigDecimal.ZERO;

        Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT r.res_id, r.status, r.car_id, r.start_date, r.end_date,
                   c.status AS car_status, c.price_per_day
            FROM public.reservations r
            JOIN public.cars c ON c.car_id = r.car_id
            WHERE r.res_id = ?
        """, rid);

        if (!"Active".equals(row.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Reservation is not in 'Active' state");
        }

        LocalDate startDate = ((java.sql.Date) row.get("start_date")).toLocalDate();
        LocalDate endDate = ((java.sql.Date) row.get("end_date")).toLocalDate();
        long days = Math.max(endDate.toEpochDay() - startDate.toEpochDay(), 1);

        BigDecimal pricePerDay = (BigDecimal) row.get("price_per_day");
        BigDecimal base = pricePerDay.multiply(BigDecimal.valueOf(days));
        BigDecimal total = base.add(damageFee).add(refuelFee);

        jdbcTemplate.update("UPDATE public.reservations SET status='Completed' WHERE res_id=?", rid);
        jdbcTemplate.update("UPDATE public.cars SET status='Available' WHERE car_id=?", row.get("car_id"));

        Integer invCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.invoices WHERE reservation_id=?",
            Integer.class, rid
        );

        if (invCount != null && invCount > 0) {
            jdbcTemplate.update("""
                UPDATE public.invoices
                SET total_amount = ?,
                    payment_status = CASE
                        WHEN ? <= COALESCE((SELECT SUM(amount) FROM public.payments WHERE invoice_id = inv_id), 0)
                        THEN 'paid'::payment_status
                        ELSE 'unpaid'::payment_status
                    END
                WHERE reservation_id = ?
            """, total, total, rid);
        } else {
            jdbcTemplate.update("""
                INSERT INTO public.invoices (reservation_id, issue_date, total_amount, payment_status)
                VALUES (?, CURRENT_DATE, ?, 'unpaid')
            """, rid, total);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("message", "Rental closed");
        response.put("total", total);

        return ResponseEntity.ok(response);
    }
}
