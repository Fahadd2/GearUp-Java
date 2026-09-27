package com.gearup.model;

/** How a payment was made (database type {@code payment_method}). */
public enum PaymentMethod implements LabeledEnum {
    CASH("cash"),
    CARD("card"),
    TRANSFER("transfer");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
