package com.gearup.service;

import com.gearup.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * Small checks for request fields. Each one returns the value when it is valid, or throws
 * a {@link BadRequestException} (HTTP 400) naming the field, so callers can write:
 *
 * <pre>{@code
 * String email = Validation.requireEmail(request.email());
 * }</pre>
 */
final class Validation {

    /** Something@something.something, with no spaces. Deliberately simple. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private Validation() {
    }

    static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new BadRequestException(field + " is required");
        }
        return value;
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value;
    }

    static String requireLength(String value, String field, int min, int max) {
        requireValue(value, field);
        if (value.length() < min || value.length() > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max + " characters");
        }
        return value;
    }

    /** Checks the format and returns the address with surrounding spaces removed. */
    static String requireEmail(String value) {
        String email = requireText(value, "email").strip();
        if (!EMAIL.matcher(email).matches()) {
            throw new BadRequestException("email is not a valid email address");
        }
        return email;
    }
}
