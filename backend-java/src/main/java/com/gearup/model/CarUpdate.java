package com.gearup.model;

import java.math.BigDecimal;

/**
 * Body of {@code PUT /cars/{id}}. Only the fields that are present (not {@code null}) are changed.
 */
public record CarUpdate(
        String brand,
        String model,
        Integer year,
        CarCategory category,
        Transmission transmission,
        BigDecimal pricePerDay,
        CarStatus status) {
}
