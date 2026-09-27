package com.gearup.model;

import java.math.BigDecimal;

/** How much of an invoice has been paid (database type {@code payment_status}). */
public enum PaymentStatus implements LabeledEnum {
    UNPAID("unpaid"),
    PARTIAL("partial"),
    PAID("paid");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * The status of an invoice of {@code total} after {@code paid} has been received.
     * Used both when a payment is recorded and when a rental is closed, so the rule lives in one place.
     */
    public static PaymentStatus forAmounts(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(total) >= 0) {
            return PAID;
        }
        return paid.signum() > 0 ? PARTIAL : UNPAID;
    }
}
