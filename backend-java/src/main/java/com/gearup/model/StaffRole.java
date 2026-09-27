package com.gearup.model;

/** Role of an employee (column {@code employees.role}). Both roles count as staff. */
public enum StaffRole implements LabeledEnum {
    EMPLOYEE("employee"),
    ADMIN("admin");

    private final String label;

    StaffRole(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
