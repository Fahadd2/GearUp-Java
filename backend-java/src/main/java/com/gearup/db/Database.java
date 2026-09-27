package com.gearup.db;

import com.gearup.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The PostgreSQL database, attached as a backing service (Factor IV).
 *
 * <p>The database is located only through the URL and credentials in {@link AppConfig}.
 * Pointing the app at a different database (local Docker, Supabase...) is a config change,
 * not a code change. All application data lives here and none is kept in memory (Factor VI).
 *
 * <p>Each call to {@link #inTransaction} opens its own connection, so worker threads never
 * share a connection and need no extra locking.
 */
public final class Database {

    private final String url;
    private final String user;
    private final String password;

    public Database(AppConfig config) {
        this.url = config.dbUrl();
        this.user = config.dbUser();
        this.password = config.dbPassword();
    }

    /**
     * Runs {@code work} in one transaction: commits if it finishes normally, rolls back if it
     * throws. Any exception thrown by {@code work} (such as an {@code ApiException}) is passed on
     * unchanged; JDBC's {@link SQLException} is wrapped in a {@link DatabaseException}.
     *
     * <pre>{@code
     * List<String> ids = database.inTransaction(tx ->
     *         tx.queryList("SELECT car_id FROM cars", row -> row.getString("car_id")));
     * }</pre>
     */
    public <T> T inTransaction(TransactionWork<T> work) {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(new Transaction(connection));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error: " + e.getMessage(), e);
        }
    }
}
