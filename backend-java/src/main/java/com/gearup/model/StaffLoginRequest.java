package com.gearup.model;

/** Body of {@code POST /auth/staff_login}. The role must match the one stored for the employee. */
public record StaffLoginRequest(String email, String password, StaffRole role) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "StaffLoginRequest[email=" + email + ", password=***, role=" + role + "]";
    }
}
