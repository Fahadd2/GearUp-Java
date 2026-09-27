package com.gearup.model;

/** Today's numbers for the staff dashboard ({@code GET /dashboard/kpis}). */
public record DashboardKpis(long todaysPickups, long todaysReturns, long activeRentals, long unpaidInvoices) {
}
