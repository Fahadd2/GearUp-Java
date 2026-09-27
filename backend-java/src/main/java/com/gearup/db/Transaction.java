package com.gearup.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs SQL on one open connection that belongs to a single transaction.
 *
 * <p>All values are passed as {@code ?} parameters, never glued into the SQL text, so user
 * input can never change the meaning of a query. Instances are created by {@link Database}
 * and are only valid inside the lambda given to {@link Database#inTransaction(TransactionWork)}.
 */
public final class Transaction {

    private final Connection connection;

    Transaction(Connection connection) {
        this.connection = connection;
    }

    /** Runs a SELECT and maps every row, e.g. {@code tx.queryList(sql, Car::fromRow)}. */
    public <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rows = statement.executeQuery()) {
            List<T> results = new ArrayList<>();
            while (rows.next()) {
                results.add(mapper.map(rows));
            }
            return results;
        }
    }

    /** Runs a SELECT and maps the first row, or returns empty if there are no rows. */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(mapper.map(rows)) : Optional.empty();
        }
    }

    /** Runs an INSERT, UPDATE or DELETE and returns how many rows it changed. */
    public int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(sql, params)) {
            return statement.executeUpdate();
        }
    }

    /** Runs a script of several SQL statements with no parameters, such as schema.sql. */
    public void executeScript(String script) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(script);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement;
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }
}
