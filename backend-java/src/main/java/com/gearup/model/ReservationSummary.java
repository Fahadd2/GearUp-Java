package com.gearup.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the staff reservation list ({@code GET /reservations}): the reservation with its
 * customer, car and invoice. Invoice fields are {@code null} if no invoice exists yet.
 */
public record ReservationSummary(
        String resId,
        String customerLicenseNo,
        String carId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        String customerName,
        String customerEmail,
        String carName,
        String invId,
        BigDecimal totalAmount,
        PaymentStatus paymentStatus) {
}
