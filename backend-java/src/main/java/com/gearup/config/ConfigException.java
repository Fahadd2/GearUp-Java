package com.gearup.config;

/**
 * Thrown at startup when a required environment variable is missing or invalid.
 * The process logs the message and exits instead of starting half-configured.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }
}
