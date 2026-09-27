package com.gearup.model;

import java.math.BigDecimal;

/** Response of {@code POST /payments/pay}, same shape as the Python API. */
public record PaymentResult(boolean ok, String invoiceId, PaymentStatus status, BigDecimal paidTotal) {
}
