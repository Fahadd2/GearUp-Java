package com.gearup.model;

/** Gearbox type of a car (database type {@code transmission_type}). */
public enum Transmission implements LabeledEnum {
    AUTO("auto"),
    MANUAL("manual");

    private final String label;

    Transmission(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
