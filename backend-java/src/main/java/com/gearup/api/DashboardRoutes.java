package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Router;
import com.gearup.service.DashboardService;

/** {@code /dashboard/*}: numbers for the staff dashboard. */
public final class DashboardRoutes {

    private DashboardRoutes() {
    }

    public static void register(Router router, DashboardService dashboard, AuthGuard guard) {
        router.get("/dashboard/kpis", guard.staffOnly((request, staff) -> dashboard.kpis()));
        router.get("/dashboard/revenue", guard.staffOnly((request, staff) -> dashboard.revenue()));
    }
}
