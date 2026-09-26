package com.gearup.exception;

/** HTTP 409 Conflict: the request clashes with existing data, such as an already-booked car. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(409, message);
    }
}
