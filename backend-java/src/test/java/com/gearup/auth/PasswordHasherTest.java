package com.gearup.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that passwords stored by the Python backend still work after the move to Java.
 *
 * <p>Every hash below was produced by passlib 1.7.4 (the version in the Python backend's
 * requirements.txt), not by this Java code, for example:
 * <pre>
 *   from passlib.hash import bcrypt_sha256
 *   bcrypt_sha256.using(rounds=4).hash("GearUp-Test-123")
 * </pre>
 * Most use 4 bcrypt rounds so the tests run fast; one uses the real default of 12.
 */
class PasswordHasherTest {

    private static final String PASSWORD = "GearUp-Test-123";

    /** "كلمة-سر-٢٠٢٥", written with escapes so the test does not depend on the editor's encoding. */
    private static final String ARABIC_PASSWORD =
            "كلمة-سر-٢٠٢٥";

    // passlib bcrypt_sha256, version 2 (passlib's default): HMAC-SHA256 pre-hash keyed with the salt.
    private static final String PASSLIB_V2 =
            "$bcrypt-sha256$v=2,t=2b,r=4$83FByNgZi3g98ewE4Pp8ku$FBxMGOow1rESw9eADYwR/raOr/TKUge";
    private static final String PASSLIB_V2_ROUNDS_12 =
            "$bcrypt-sha256$v=2,t=2b,r=12$hhdDokIfBXzB0ZydUMQ2L.$U4Inl9/yKJ/MtoC8Ot4nukWxBd.kSAq";
    private static final String PASSLIB_V2_ARABIC =
            "$bcrypt-sha256$v=2,t=2b,r=4$Qmm2ugSv3kFO1M9nkUV0v.$7s9AwJqeWpYe6kLwXCz2DOOPB3/nqFu";

    // passlib bcrypt_sha256, legacy version 1: plain SHA-256 pre-hash.
    private static final String PASSLIB_V1 =
            "$bcrypt-sha256$2b,4$3RyEvLVpkyrJHhuu47rjkO$m3ZQWIFpAh98tiVpTY3yqp2KuCtB..i";

    // passlib plain bcrypt, which the Python staff login also accepted.
    private static final String PASSLIB_PLAIN_BCRYPT =
            "$2b$04$FS8Wb/5GQc6GJobZ5YZgvuudPrHMob6HC9gYNoXtToX4Bg6VFjuTW";

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void acceptsPasslibBcryptSha256Version2Hash() {
        assertTrue(hasher.matches(PASSWORD, PASSLIB_V2));
        assertFalse(hasher.matches("wrong-password", PASSLIB_V2));
    }

    @Test
    void acceptsPasslibHashWithDefaultTwelveRounds() {
        assertTrue(hasher.matches(PASSWORD, PASSLIB_V2_ROUNDS_12));
    }

    @Test
    void acceptsNonAsciiPasswordHashedByPasslib() {
        assertTrue(hasher.matches(ARABIC_PASSWORD, PASSLIB_V2_ARABIC));
        assertFalse(hasher.matches(PASSWORD, PASSLIB_V2_ARABIC));
    }

    @Test
    void acceptsLegacyPasslibVersion1Hash() {
        assertTrue(hasher.matches(PASSWORD, PASSLIB_V1));
        assertFalse(hasher.matches("wrong-password", PASSLIB_V1));
    }

    @Test
    void acceptsPlainBcryptHash() {
        assertTrue(hasher.matches(PASSWORD, PASSLIB_PLAIN_BCRYPT));
        assertFalse(hasher.matches("wrong-password", PASSLIB_PLAIN_BCRYPT));
    }

    @Test
    void doesNotConfuseVersions() {
        // Treating a v2 hash as v1 (plain SHA-256) or as plain bcrypt must fail.
        String v2AsV1 = PASSLIB_V2.replace("v=2,t=2b,r=4", "2b,4");
        assertFalse(hasher.matches(PASSWORD, v2AsV1));
    }

    @Test
    void newHashesUsePasslibFormatAndVerify() {
        String hash = hasher.hash(PASSWORD);

        assertTrue(hash.startsWith("$bcrypt-sha256$v=2,t=2b,r=12$"), hash);
        assertTrue(hasher.matches(PASSWORD, hash));
        assertFalse(hasher.matches("wrong-password", hash));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertFalse(hasher.hash(PASSWORD).equals(hasher.hash(PASSWORD)));
    }

    @Test
    void rejectsMissingOrUnknownHashes() {
        assertFalse(hasher.matches(PASSWORD, null));
        assertFalse(hasher.matches(PASSWORD, ""));
        assertFalse(hasher.matches(PASSWORD, "plain-text-password"));
        assertFalse(hasher.matches(PASSWORD, "$pbkdf2-sha256$29000$abc$def"));
        assertFalse(hasher.matches(null, PASSLIB_V2));
    }
}
