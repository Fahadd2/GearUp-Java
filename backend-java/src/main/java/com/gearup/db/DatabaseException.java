package com.gearup.db;

/**
 * An unexpected database failure (connection lost, SQL error, constraint violated...).
 *
 * <p>JDBC reports problems with the checked {@link java.sql.SQLException}. {@link Database}
 * wraps it in this unchecked exception so that services do not need {@code throws SQLException}
 * on every method. The Router reports it to the client as a 500 error and logs the details.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
