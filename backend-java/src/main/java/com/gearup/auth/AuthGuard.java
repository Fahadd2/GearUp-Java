package com.gearup.auth;

import com.gearup.exception.ForbiddenException;
import com.gearup.exception.UnauthorizedException;
import com.gearup.http.Request;
import com.gearup.http.RouteHandler;

import java.util.Locale;

/**
 * Protects routes that need a login. Each method wraps a handler lambda and returns a normal
 * {@link RouteHandler} that checks the {@code Authorization: Bearer <token>} header first:
 *
 * <pre>{@code
 * router.put("/cars/{id}", guard.staffOnly((request, staff) -> ...));
 * }</pre>
 *
 * <ul>
 *   <li>No token, or an invalid/expired one: 401 {@link UnauthorizedException}.</li>
 *   <li>A valid token of the wrong kind (e.g. a customer on a staff route): 403 {@link ForbiddenException}.</li>
 * </ul>
 */
public final class AuthGuard {

    private static final String BEARER_PREFIX = "bearer ";

    private final TokenService tokens;

    public AuthGuard(TokenService tokens) {
        this.tokens = tokens;
    }

    /** Any logged-in user, customer or staff. */
    public RouteHandler anyUser(AuthenticatedHandler<LoggedInUser> handler) {
        return request -> handler.handle(request, authenticate(request));
    }

    /** Customers only. Staff get 403. */
    public RouteHandler customerOnly(AuthenticatedHandler<CustomerUser> handler) {
        return request -> {
            if (!(authenticate(request) instanceof CustomerUser customer)) {
                throw new ForbiddenException("Only customers can do this. Please sign in with a customer account.");
            }
            return handler.handle(request, customer);
        };
    }

    /** Employees and admins only. Customers get 403. */
    public RouteHandler staffOnly(AuthenticatedHandler<StaffUser> handler) {
        return request -> {
            if (!(authenticate(request) instanceof StaffUser staff)) {
                throw new ForbiddenException("Only staff members can do this.");
            }
            return handler.handle(request, staff);
        };
    }

    private LoggedInUser authenticate(Request request) {
        String header = request.header("Authorization")
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        if (!header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Not authenticated");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return tokens.verify(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired token. Please sign in again."));
    }
}
