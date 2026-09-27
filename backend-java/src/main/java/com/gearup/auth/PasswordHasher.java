package com.gearup.auth;

import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hashes and checks passwords in the same formats as the Python backend (passlib 1.7.4),
 * so existing users can keep logging in and new hashes still work in the Python version.
 *
 * <p>Accepted stored formats:
 * <ul>
 *   <li>{@code $bcrypt-sha256$v=2,t=2b,r=12$<salt>$<checksum>}: passlib's default. The password is
 *       first run through HMAC-SHA256 keyed with the salt, base64-encoded, then bcrypt-hashed.</li>
 *   <li>{@code $bcrypt-sha256$2b,12$<salt>$<checksum>}: passlib's older version 1, which uses a
 *       plain SHA-256 digest instead of HMAC.</li>
 *   <li>{@code $2b$12$...}: plain bcrypt, which the Python staff login also accepted.</li>
 * </ul>
 * The pre-hash step exists because bcrypt ignores everything after 72 bytes and stops at a zero
 * byte; base64 of a SHA-256 digest is always 44 safe characters. New hashes use the v2 format.
 *
 * <p>jBCrypt only understands the {@code $2a$} prefix. For passwords shorter than 255 bytes,
 * {@code $2a$}, {@code $2b$} and {@code $2y$} produce identical results, so the prefix is rewritten.
 */
public final class PasswordHasher {

    private static final int DEFAULT_ROUNDS = 12;
    private static final int CHECKSUM_LENGTH = 31;

    private static final Pattern V2_FORMAT = Pattern.compile(
            "\\$bcrypt-sha256\\$v=2,t=2[ab],r=(?<rounds>\\d{1,2})\\$(?<salt>[./A-Za-z0-9]{22})\\$(?<checksum>[./A-Za-z0-9]{31})");
    private static final Pattern V1_FORMAT = Pattern.compile(
            "\\$bcrypt-sha256\\$2[ab],(?<rounds>\\d{1,2})\\$(?<salt>[./A-Za-z0-9]{22})\\$(?<checksum>[./A-Za-z0-9]{31})");
    private static final Pattern PLAIN_BCRYPT_FORMAT = Pattern.compile(
            "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    /** Creates a new passlib-compatible {@code bcrypt-sha256} (v2) hash with a random salt. */
    public String hash(String password) {
        String salt = BCrypt.gensalt(DEFAULT_ROUNDS).substring("$2a$12$".length());
        String checksum = bcryptChecksum(hmacSha256Key(salt, password), DEFAULT_ROUNDS, salt);
        return "$bcrypt-sha256$v=2,t=2b,r=" + DEFAULT_ROUNDS + "$" + salt + "$" + checksum;
    }

    /** True if {@code password} matches {@code storedHash}. Unknown or empty hashes never match. */
    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }

        Matcher v2 = V2_FORMAT.matcher(storedHash);
        if (v2.matches()) {
            return checksumMatches(hmacSha256Key(v2.group("salt"), password), v2);
        }

        Matcher v1 = V1_FORMAT.matcher(storedHash);
        if (v1.matches()) {
            return checksumMatches(sha256Key(password), v1);
        }

        if (PLAIN_BCRYPT_FORMAT.matcher(storedHash).matches()) {
            return BCrypt.checkpw(password, "$2a$" + storedHash.substring("$2b$".length()));
        }
        return false;
    }

    private static boolean checksumMatches(String key, Matcher storedParts) {
        int rounds = Integer.parseInt(storedParts.group("rounds"));
        String actual = bcryptChecksum(key, rounds, storedParts.group("salt"));
        // Compare in constant time so response timing does not leak how many characters matched.
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                storedParts.group("checksum").getBytes(StandardCharsets.US_ASCII));
    }

    /** Runs bcrypt and keeps only the 31-character checksum at the end of its output. */
    private static String bcryptChecksum(String key, int rounds, String salt) {
        String full = BCrypt.hashpw(key, String.format("$2a$%02d$%s", rounds, salt));
        return full.substring(full.length() - CHECKSUM_LENGTH);
    }

    /** Version 2 pre-hash: base64(HMAC-SHA256(key = salt, message = password)). */
    private static String hmacSha256Key(String salt, String password) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available in the JDK", e);
        }
    }

    /** Version 1 pre-hash: base64(SHA-256(password)). */
    private static String sha256Key(String password) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(sha256.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
