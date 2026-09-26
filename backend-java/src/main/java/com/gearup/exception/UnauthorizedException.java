package com.gearup.exception;

/** HTTP 401 Unauthorized: no valid login token was sent. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(401, message);
    }
}
