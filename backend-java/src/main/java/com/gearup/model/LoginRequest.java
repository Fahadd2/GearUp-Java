package com.gearup.model;

/** Body of {@code POST /auth/login}. */
public record LoginRequest(String email, String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
