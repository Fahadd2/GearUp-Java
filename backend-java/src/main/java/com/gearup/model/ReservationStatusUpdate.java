package com.gearup.model;

/** Body of {@code PUT /reservations/{id}}. */
public record ReservationStatusUpdate(ReservationStatus status) {
}
