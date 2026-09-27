package com.gearup.auth;

import com.gearup.model.StaffRole;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    /**
     * Signed by PyJWT 2.9.0 (the Python backend's library) with {@link #SECRET}:
     * <pre>
     *   jwt.encode({"sub": "1234567890", "email": "sara@example.com",
     *               "iat": 1767225600, "exp": 4102444800}, secret, algorithm="HS256")
     * </pre>
     */
    private static final String PYJWT_CUSTOMER_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                    + ".eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJzYXJhQGV4YW1wbGUuY29tIiwiaWF0IjoxNzY3MjI1NjAwLCJleHAiOjQxMDI0NDQ4MDB9"
                    + ".ENZ5ENRI5rlvLSxA1iiXz0dnh0MIcZEZjtxbJ1yD6zM";

    private final TokenService tokens = new TokenService(SECRET, fixedClock(NOW));

    @Test
    void customerTokenRoundTrip() {
        String token = tokens.issue(new CustomerUser("1234567890", "sara@example.com"));

        assertEquals(Optional.of(new CustomerUser("1234567890", "sara@example.com")), tokens.verify(token));
    }

    @Test
    void staffTokenKeepsRole() {
        StaffUser admin = new StaffUser("EMP-1", "admin@gearup.sa", StaffRole.ADMIN);

        assertEquals(Optional.of(admin), tokens.verify(tokens.issue(admin)));
    }

    @Test
    void acceptsTokenSignedByPythonBackend() {
        assertEquals(Optional.of(new CustomerUser("1234567890", "sara@example.com")),
                tokens.verify(PYJWT_CUSTOMER_TOKEN));
    }

    @Test
    void rejectsExpiredToken() {
        String token = tokens.issue(new CustomerUser("1234567890", "sara@example.com"));
        TokenService threeHoursLater = new TokenService(SECRET, fixedClock(NOW.plus(Duration.ofHours(3))));

        assertTrue(threeHoursLater.verify(token).isEmpty());
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        TokenService otherServer = new TokenService("a-completely-different-secret-of-32-chars", fixedClock(NOW));
        String token = otherServer.issue(new CustomerUser("1234567890", "sara@example.com"));

        assertTrue(tokens.verify(token).isEmpty());
    }

    @Test
    void rejectsCustomerTokenEditedToClaimStaffRole() {
        String token = tokens.issue(new CustomerUser("1234567890", "sara@example.com"));
        String[] parts = token.split("\\.");
        String forgedClaims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"EMP-1\",\"email\":\"x@y.z\",\"role\":\"admin\",\"iat\":1767225600,\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));

        assertTrue(tokens.verify(parts[0] + "." + forgedClaims + "." + parts[2]).isEmpty());
    }

    @Test
    void rejectsMalformedTokens() {
        assertTrue(tokens.verify("").isEmpty());
        assertTrue(tokens.verify("not-a-token").isEmpty());
        assertTrue(tokens.verify("a.b.c").isEmpty());
        assertTrue(tokens.verify(PYJWT_CUSTOMER_TOKEN + "x").isEmpty());
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
