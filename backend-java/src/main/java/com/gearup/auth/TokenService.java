package com.gearup.auth;

import com.gearup.http.Json;
import com.gearup.model.LabeledEnum;
import com.gearup.model.StaffRole;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and checks login tokens: JSON Web Tokens signed with HMAC-SHA256 ("HS256"), built with
 * the JDK only. The format matches the Python backend (PyJWT), so tokens signed with the same
 * {@code GEARUP_JWT_SECRET} are accepted by both.
 *
 * <p>Tokens make the server stateless (Factor VI): nothing about a login is stored in memory;
 * every request carries its own signed proof of who the user is.
 *
 * <p>Claims: {@code sub} (license number or employee id), {@code email}, {@code iat}, {@code exp},
 * and for staff only, {@code role} ("employee" or "admin"). A token without a role is a customer's.
 */
public final class TokenService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(120);
    private static final String ALGORITHM = "HS256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private record Header(String alg, String typ) {
    }

    private record Claims(String sub, String email, String role, Long iat, Long exp) {
    }

    private final byte[] secret;
    private final Clock clock;

    /**
     * @param secret the signing key, from {@code GEARUP_JWT_SECRET}
     * @param clock  the source of "now"; tests pass a fixed clock to check expiry
     */
    public TokenService(String secret, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** Creates a signed token for the user, valid for {@link #TOKEN_LIFETIME}. */
    public String issue(LoggedInUser user) {
        long now = clock.instant().getEpochSecond();
        String role = user instanceof StaffUser staff ? staff.role().label() : null;
        Claims claims = new Claims(user.subject(), user.email(), role, now, now + TOKEN_LIFETIME.toSeconds());

        String unsigned = encode(HEADER_JSON) + "." + encode(Json.toJson(claims));
        return unsigned + "." + sign(unsigned);
    }

    /**
     * Checks a token's signature and expiry and returns its user.
     * Returns empty for any token that is malformed, tampered with, signed with another key, or expired.
     */
    public Optional<LoggedInUser> verify(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        String unsigned = parts[0] + "." + parts[1];
        byte[] expectedSignature = sign(unsigned).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedSignature, parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }

        try {
            Header header = Json.fromJson(decode(parts[0]), Header.class);
            Claims claims = Json.fromJson(decode(parts[1]), Claims.class);
            long now = clock.instant().getEpochSecond();
            if (!ALGORITHM.equals(header.alg()) || claims.sub() == null
                    || claims.exp() == null || claims.exp() <= now) {
                return Optional.empty();
            }
            return Optional.of(toUser(claims));
        } catch (RuntimeException e) {
            // Bad base64, bad JSON or an unknown role: treat it like any other invalid token.
            return Optional.empty();
        }
    }

    private static LoggedInUser toUser(Claims claims) {
        if (claims.role() == null) {
            return new CustomerUser(claims.sub(), claims.email());
        }
        StaffRole role = LabeledEnum.fromLabel(StaffRole.class, claims.role());
        return new StaffUser(claims.sub(), claims.email(), role);
    }

    /**
     * HMAC-SHA256 signature, base64url-encoded. A new {@link Mac} is created per call because
     * {@code Mac} objects are not thread-safe and this method runs on many worker threads at once.
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available in the JDK", e);
        }
    }

    private static String encode(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String base64) {
        return new String(BASE64_URL_DECODER.decode(base64), StandardCharsets.UTF_8);
    }
}
