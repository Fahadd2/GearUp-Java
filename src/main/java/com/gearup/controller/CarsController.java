package com.gearup.controller;

import com.gearup.dto.CarUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.*;

@RestController
@RequestMapping("/cars")
public class CarsController {

    private final JdbcTemplate jdbcTemplate;

    public CarsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listCars(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer seats,
            @RequestParam(required = false) String transmission,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                car_id AS id,
                plate_no,
                brand, model, year,
                category::text AS category,
                fuel_type,
                color,
                seats,
                transmission::text AS transmission,
                price_per_day::float AS price_per_day,
                status::text AS status,
                COALESCE(photo_url,'') AS photo_url,
                created_at
            FROM public.cars
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            sql.append(" AND category = ?::car_category");
            params.add(category);
        }
        if (seats != null) {
            sql.append(" AND seats >= ?");
            params.add(seats);
        }
        if (transmission != null && !transmission.isEmpty()) {
            sql.append(" AND transmission = ?::transmission_type");
            params.add(transmission);
        }
        if (minPrice != null) {
            sql.append(" AND price_per_day >= ?");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND price_per_day <= ?");
            params.add(maxPrice);
        }

        sql.append(" ORDER BY price_per_day, brand, model");

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{carId}")
    public ResponseEntity<?> updateCar(
            @PathVariable String carId,
            @Valid @RequestBody CarUpdateRequest request
    ) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.cars WHERE car_id = ?",
            Integer.class, carId
        );

        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found");
        }

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (request.getBrand() != null) {
            updates.add("brand = ?");
            params.add(request.getBrand());
        }
        if (request.getModel() != null) {
            updates.add("model = ?");
            params.add(request.getModel());
        }
        if (request.getYear() != null) {
            updates.add("year = ?");
            params.add(request.getYear());
        }
        if (request.getCategory() != null) {
            updates.add("category = ?::car_category");
            params.add(request.getCategory());
        }
        if (request.getTransmission() != null) {
            updates.add("transmission = ?::transmission_type");
            params.add(request.getTransmission());
        }
        if (request.getPricePerDay() != null) {
            updates.add("price_per_day = ?");
            params.add(request.getPricePerDay());
        }
        if (request.getStatus() != null) {
            updates.add("status = ?::car_status");
            params.add(request.getStatus());
        }

        if (updates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }

        params.add(carId);
        String updateSql = "UPDATE public.cars SET " + String.join(", ", updates) + " WHERE car_id = ?";
        jdbcTemplate.update(updateSql, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Car updated successfully");
        response.put("car_id", carId);

        return ResponseEntity.ok(response);
    }
}
