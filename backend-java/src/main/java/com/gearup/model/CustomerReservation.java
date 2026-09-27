package com.gearup.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One of the logged-in customer's own reservations ({@code GET /reservations/my_reservations}). */
public record CustomerReservation(
        String resId,
        String carId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        String carName,
        String photoUrl,
        BigDecimal totalAmount,
        PaymentStatus paymentStatus) {
}
