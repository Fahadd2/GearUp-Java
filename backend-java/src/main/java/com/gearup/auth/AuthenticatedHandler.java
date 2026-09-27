package com.gearup.auth;

import com.gearup.http.Request;

/**
 * A route handler that also receives the logged-in user, already checked by {@link AuthGuard}.
 * {@code U} is the kind of user the route allows, e.g. {@code AuthenticatedHandler<StaffUser>}.
 */
@FunctionalInterface
public interface AuthenticatedHandler<U extends LoggedInUser> {

    Object handle(Request request, U user) throws Exception;
}
