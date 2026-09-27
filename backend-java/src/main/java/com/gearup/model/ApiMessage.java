package com.gearup.model;

/** A simple success response, e.g. {@code {"ok": true, "message": "Password has been reset"}}. */
public record ApiMessage(boolean ok, String message) {
}
