package com.gearup.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Search options for {@code GET /cars}. A {@code null} field means "do not filter on this".
 * The date range only applies when both dates are given.
 */
public record CarFilter(
        CarCategory category,
        Integer minSeats,
        Transmission transmission,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate startDate,
        LocalDate endDate) {
}
