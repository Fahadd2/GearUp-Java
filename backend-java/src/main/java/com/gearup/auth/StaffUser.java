package com.gearup.auth;

import com.gearup.model.StaffRole;

/** A logged-in employee or admin, identified by their employee id (e.g. {@code EMP-3}). */
public record StaffUser(String empId, String email, StaffRole role) implements LoggedInUser {

    @Override
    public String subject() {
        return empId;
    }
}
