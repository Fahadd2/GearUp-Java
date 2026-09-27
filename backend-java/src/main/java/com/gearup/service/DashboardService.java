package com.gearup.service;

import com.gearup.db.Database;
import com.gearup.model.DashboardKpis;
import com.gearup.model.RevenueStats;

/**
 * Numbers for the staff dashboard. Each method answers with a single SQL query, and "today"
 * is the database's {@code CURRENT_DATE}, so every app instance agrees on the date.
 */
public final class DashboardService {

    private final Database database;

    public DashboardService(Database database) {
        this.database = database;
    }

    public DashboardKpis kpis() {
        return database.inTransaction(tx -> tx.queryOne("""
                        SELECT
                          (SELECT COUNT(*) FROM public.reservations
                            WHERE start_date = CURRENT_DATE AND status = 'Reserved') AS todays_pickups,
                          (SELECT COUNT(*) FROM public.reservations
                            WHERE end_date = CURRENT_DATE AND status = 'Active')     AS todays_returns,
                          (SELECT COUNT(*) FROM public.reservations
                            WHERE status = 'Active')                                  AS active_rentals,
                          (SELECT COUNT(*) FROM public.invoices
                            WHERE payment_status IN ('unpaid', 'partial'))           AS unpaid_invoices""",
                row -> new DashboardKpis(
                        row.getLong("todays_pickups"),
                        row.getLong("todays_returns"),
                        row.getLong("active_rentals"),
                        row.getLong("unpaid_invoices"))).orElseThrow());
    }

    /** Paid revenue in total and this month, and the value of invoices not fully paid yet. */
    public RevenueStats revenue() {
        return database.inTransaction(tx -> tx.queryOne("""
                        SELECT
                          COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'paid'), 0)
                              AS total_revenue,
                          COALESCE(SUM(total_amount) FILTER (WHERE payment_status IN ('unpaid', 'partial')), 0)
                              AS pending_revenue,
                          COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'paid'
                              AND date_trunc('month', issue_date) = date_trunc('month', CURRENT_DATE)), 0)
                              AS this_month_revenue
                        FROM public.invoices""",
                row -> new RevenueStats(
                        row.getBigDecimal("total_revenue"),
                        row.getBigDecimal("pending_revenue"),
                        row.getBigDecimal("this_month_revenue"))).orElseThrow());
    }
}
