package com.gearup.model;

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
}
