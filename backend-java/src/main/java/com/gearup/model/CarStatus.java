package com.gearup.model;

/** Availability of a car (database type {@code car_status}). */
public enum CarStatus implements LabeledEnum {
    AVAILABLE("Available"),
    RESERVED("Reserved"),
    RENTED("Rented"),
    MAINTENANCE("Maintenance");

    private final String label;

    CarStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
