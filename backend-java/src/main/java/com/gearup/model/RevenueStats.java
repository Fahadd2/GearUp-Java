package com.gearup.model;

import java.math.BigDecimal;

/** Revenue totals for the staff dashboard ({@code GET /dashboard/revenue}), in SAR. */
public record RevenueStats(BigDecimal totalRevenue, BigDecimal pendingRevenue, BigDecimal thisMonthRevenue) {
}
