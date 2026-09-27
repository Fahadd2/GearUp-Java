package com.gearup.model;

/** A customer's public profile, as returned after signing up or logging in. */
public record Customer(String licenseNo, String firstName, String lastName, String email) {
}
