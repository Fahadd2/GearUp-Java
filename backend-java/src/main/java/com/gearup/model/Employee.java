package com.gearup.model;

/** A staff member's public profile, as returned after a staff login. */
public record Employee(String id, String email, String firstName, String lastName, StaffRole role) {
}
