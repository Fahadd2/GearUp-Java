package com.gearup.model;

/** Response of {@code POST /auth/staff_login}. */
public record StaffSession(String token, Employee employee) {
}
