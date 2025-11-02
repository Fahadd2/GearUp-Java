package com.gearup.controller;

import com.gearup.dto.ReservationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/reservations")
public class ReservationsController {

    private final JdbcTemplate jdbcTemplate;

    public ReservationsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/create_auth")
    public ResponseEntity<?> createReservation(
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String licenseNo = (String) authentication.getPrincipal();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        // Validate dates
        if (startDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start_date cannot be in the past");
        }
        if (endDate.isBefore(startDate) || endDate.isEqual(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end_date must be after start_date");
        }

        // Check for overlapping reservations
        Integer clash = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM public.reservations
            WHERE car_id = ?
              AND status IN ('Reserved','Active')
              AND NOT (? <= start_date OR ? >= end_date)
        """, Integer.class, request.getCarId(), endDate, startDate);

        if (clash != null && clash > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Car not available for selected dates");
        }

        // Calculate total
        BigDecimal total = calculateTotal(request.getCarId(), startDate, endDate);

        // Create reservation
        Map<String, Object> reservation = jdbcTemplate.queryForMap("""
            INSERT INTO public.reservations (
                customer_license_no, car_id, start_date, end_date, status
            ) VALUES (?, ?, ?, ?, 'Reserved')
            RETURNING res_id
        """, licenseNo, request.getCarId(), startDate, endDate);

        String resId = (String) reservation.get("res_id");

        // Create invoice
        Map<String, Object> invoice = jdbcTemplate.queryForMap("""
            INSERT INTO public.invoices (reservation_id, total_amount, payment_status)
            VALUES (?, ?, 'unpaid')
            RETURNING inv_id, total_amount
        """, resId, total);

        Map<String, Object> response = new HashMap<>();
        response.put("reservation_id", resId);
        response.put("invoice_id", invoice.get("inv_id"));
        response.put("total_amount", invoice.get("total_amount"));

        return ResponseEntity.ok(response);
    }

    private BigDecimal calculateTotal(String carId, LocalDate start, LocalDate end) {
        long days = end.toEpochDay() - start.toEpochDay();
        if (days < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum rental is 1 day");
        }

        BigDecimal pricePerDay = jdbcTemplate.queryForObject(
            "SELECT price_per_day FROM public.cars WHERE car_id = ?",
            BigDecimal.class, carId
        );

        if (pricePerDay == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found");
        }

        return pricePerDay.multiply(BigDecimal.valueOf(days));
    }
}
