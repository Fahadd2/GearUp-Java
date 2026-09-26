package com.gearup.exception;

/** HTTP 404 Not Found: the requested resource does not exist. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(404, message);
    }
}
