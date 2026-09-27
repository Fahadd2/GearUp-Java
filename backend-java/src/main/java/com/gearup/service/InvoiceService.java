package com.gearup.service;

import com.gearup.db.Database;
import com.gearup.model.Invoice;
import com.gearup.model.LabeledEnum;
import com.gearup.model.PaymentStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Listing invoices for staff. */
public final class InvoiceService {

    public static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final Database database;

    public InvoiceService(Database database) {
        this.database = database;
    }

    /** The newest invoices first; {@code limit} is clamped to 1..200 like the Python API. */
    public List<Invoice> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return database.inTransaction(tx -> tx.queryList("""
                        SELECT i.inv_id, i.reservation_id, i.issue_date, i.total_amount, i.payment_status,
                               i.created_at, r.customer_license_no, r.car_id, r.start_date, r.end_date
                        FROM public.invoices i
                        JOIN public.reservations r ON r.res_id = i.reservation_id
                        ORDER BY i.created_at DESC
                        LIMIT ?""",
                InvoiceService::toInvoice, safeLimit));
    }

    private static Invoice toInvoice(ResultSet row) throws SQLException {
        return new Invoice(
                row.getString("inv_id"),
                row.getString("reservation_id"),
                row.getObject("issue_date", LocalDate.class),
                row.getBigDecimal("total_amount"),
                LabeledEnum.fromLabel(PaymentStatus.class, row.getString("payment_status")),
                row.getObject("created_at", OffsetDateTime.class),
                row.getString("customer_license_no"),
                row.getString("car_id"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class));
    }
}
