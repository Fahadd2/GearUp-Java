package com.gearup.db;

import java.sql.SQLException;

/**
 * A unit of database work that runs inside one transaction and produces a result of type {@code T}.
 * See {@link Database#inTransaction(TransactionWork)}.
 */
@FunctionalInterface
public interface TransactionWork<T> {

    T run(Transaction tx) throws SQLException;
}
