package com.gearup.model;

import java.time.LocalDate;

/** Body of {@code POST /auth/signup}. Dates are sent as "YYYY-MM-DD". */
public record SignUpRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        String licenseNo,
        LocalDate licenseExpiry,
        LocalDate dateOfBirth,
        String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "SignUpRequest[email=" + email + ", licenseNo=" + licenseNo + ", password=***]";
    }
}
