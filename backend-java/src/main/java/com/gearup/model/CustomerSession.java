package com.gearup.model;

/** Response of {@code POST /auth/signup} and {@code POST /auth/login}. */
public record CustomerSession(String token, Customer customer) {
}
