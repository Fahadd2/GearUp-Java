package com.gearup.exception;

/** HTTP 400 Bad Request: the request is malformed or breaks a business rule. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(400, message);
    }
}
