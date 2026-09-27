package com.gearup.model;

import java.math.BigDecimal;

/** Body of {@code POST /payments/pay}. The staff member recording it comes from the login token. */
public record PaymentRequest(String invoiceId, PaymentMethod method, BigDecimal amount, String reference) {
}
