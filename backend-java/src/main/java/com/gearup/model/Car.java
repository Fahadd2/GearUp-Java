package com.gearup.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A car in the rental fleet, as returned by {@code GET /cars}.
 *
 * <p>Field names become snake_case in JSON ({@code pricePerDay} → {@code price_per_day}).
 * Money is a {@link BigDecimal} so prices and totals never suffer floating-point rounding.
 */
public record Car(
        String id,
        String plateNo,
        String brand,
        String model,
        int year,
        CarCategory category,
        String fuelType,
        String color,
        int seats,
        Transmission transmission,
        BigDecimal pricePerDay,
        CarStatus status,
        String photoUrl,
        OffsetDateTime createdAt) {
}
