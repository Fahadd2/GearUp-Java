package com.gearup.model;

/** Size class of a car (database type {@code car_category}). */
public enum CarCategory implements LabeledEnum {
    SMALL("small"),
    LARGE("large");

    private final String label;

    CarCategory(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
