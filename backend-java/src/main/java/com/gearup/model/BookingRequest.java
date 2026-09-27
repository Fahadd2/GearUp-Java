package com.gearup.model;

import java.time.LocalDate;

/** Body of {@code POST /reservations/create_auth}. The customer comes from the login token. */
public record BookingRequest(String carId, LocalDate startDate, LocalDate endDate) {
}
