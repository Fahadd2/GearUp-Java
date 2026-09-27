package com.gearup.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** An invoice with the main details of its reservation, as listed by {@code GET /invoices}. */
public record Invoice(
        String invId,
        String reservationId,
        LocalDate issueDate,
        BigDecimal totalAmount,
        PaymentStatus paymentStatus,
        OffsetDateTime createdAt,
        String customerLicenseNo,
        String carId,
        LocalDate startDate,
        LocalDate endDate) {
}
