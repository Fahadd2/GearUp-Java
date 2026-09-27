package com.gearup.model;

/**
 * Where a reservation is in its life cycle (database type {@code reservation_status}):
 * Reserved → Active (car picked up) → Completed (car returned), or Cancelled.
 */
public enum ReservationStatus implements LabeledEnum {
    RESERVED("Reserved", CarStatus.RESERVED),
    ACTIVE("Active", CarStatus.RENTED),
    COMPLETED("Completed", CarStatus.AVAILABLE),
    CANCELLED("Cancelled", CarStatus.AVAILABLE);

    private final String label;
    private final CarStatus carStatus;

    ReservationStatus(String label, CarStatus carStatus) {
        this.label = label;
        this.carStatus = carStatus;
    }

    @Override
    public String label() {
        return label;
    }

    /** The status the booked car should have while its reservation is in this state. */
    public CarStatus carStatus() {
        return carStatus;
    }
}
