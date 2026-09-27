package com.gearup.model;

import java.math.BigDecimal;

/** Response of {@code POST /reservations/create_auth}, same shape as the Python API. */
public record BookingConfirmation(String reservationId, String invoiceId, BigDecimal totalAmount) {
}
