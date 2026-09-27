package com.gearup.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Turns the current row of a {@link ResultSet} into an object of type {@code T}.
 * Usually written as a lambda, for example {@code rs -> rs.getString("car_id")}.
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet row) throws SQLException;
}
