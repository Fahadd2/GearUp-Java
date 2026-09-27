package com.gearup.model;

/** Body of {@code POST /rentals/start}: the customer picks up the car. */
public record RentalStartRequest(String reservationId) {
}
