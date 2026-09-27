package com.gearup.model;

import java.math.BigDecimal;

/** Body of {@code POST /rentals/close}: the car is returned. Missing fees count as 0. */
public record RentalCloseRequest(String reservationId, BigDecimal damageFee, BigDecimal refuelFee) {
}
