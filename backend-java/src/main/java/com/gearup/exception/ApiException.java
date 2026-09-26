package com.gearup.exception;

/**
 * An expected error that should be reported to the client with a specific HTTP status.
 *
 * <p>Services throw a subclass (for example {@link NotFoundException}) and never deal with HTTP
 * directly. The {@code Router} catches every {@code ApiException} at the request boundary and turns
 * it into a JSON response of the form {@code {"detail": "..."}}, the same shape the Python API used.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
