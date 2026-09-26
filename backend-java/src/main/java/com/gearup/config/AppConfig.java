package com.gearup.config;

import java.util.Map;

/**
 * All configuration for one deploy, read from environment variables only (Factor III).
 *
 * <p>Nothing here has a hardcoded value except the default port. The same build runs in
 * development, testing and production; only the environment changes.
 *
 * @param port       HTTP port to bind to ({@code PORT}, default 8080)
 * @param dbUrl      JDBC URL of the PostgreSQL database ({@code GEARUP_DB_URL})
 * @param dbUser     database user ({@code GEARUP_DB_USER})
 * @param dbPassword database password ({@code GEARUP_DB_PASSWORD})
 * @param jwtSecret  key used to sign login tokens ({@code GEARUP_JWT_SECRET})
 */
public record AppConfig(int port, String dbUrl, String dbUser, String dbPassword, String jwtSecret) {

    private static final int DEFAULT_PORT = 8080;
    private static final int MIN_JWT_SECRET_LENGTH = 32;

    /** Reads the configuration of the current process. */
    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    /** Builds the configuration from any map of variables, so it can be tested without a real environment. */
    static AppConfig from(Map<String, String> env) {
        int port = parsePort(env.get("PORT"));
        String dbUrl = required(env, "GEARUP_DB_URL");
        String dbUser = required(env, "GEARUP_DB_USER");
        String dbPassword = required(env, "GEARUP_DB_PASSWORD");
        String jwtSecret = required(env, "GEARUP_JWT_SECRET");

        if (jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new ConfigException("GEARUP_JWT_SECRET must be at least " + MIN_JWT_SECRET_LENGTH + " characters long");
        }
        return new AppConfig(port, dbUrl, dbUser, dbPassword, jwtSecret);
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new ConfigException("Missing required environment variable " + name + " (see .env.example)");
        }
        return value.trim();
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new ConfigException("PORT must be between 1 and 65535, got " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new ConfigException("PORT must be a number, got '" + value + "'");
        }
    }

    /** Never print secrets, even by accident in a log line. */
    @Override
    public String toString() {
        return "AppConfig[port=" + port + ", dbUrl=" + dbUrl + ", dbUser=" + dbUser
                + ", dbPassword=***, jwtSecret=***]";
    }
}
