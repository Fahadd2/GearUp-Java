package com.gearup.auth;

/** A logged-in customer, identified by their driving license number. */
public record CustomerUser(String licenseNo, String email) implements LoggedInUser {

    @Override
    public String subject() {
        return licenseNo;
    }
}
