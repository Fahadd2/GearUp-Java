package com.gearup.exception;

/** HTTP 403 Forbidden: the caller is logged in but not allowed to do this. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(403, message);
    }
}
