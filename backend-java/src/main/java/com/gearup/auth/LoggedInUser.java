package com.gearup.auth;

/**
 * Whoever sent a request with a valid login token. It is either a customer or a staff member,
 * and nothing else: the interface is {@code sealed}, so the compiler knows these are the only
 * two kinds.
 */
public sealed interface LoggedInUser permits CustomerUser, StaffUser {

    /** The token subject: a customer's license number, or an employee id such as {@code EMP-3}. */
    String subject();

    String email();
}
