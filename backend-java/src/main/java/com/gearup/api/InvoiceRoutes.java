package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Router;
import com.gearup.service.InvoiceService;

/** {@code GET /invoices?limit=100}: the newest invoices, for staff. */
public final class InvoiceRoutes {

    private InvoiceRoutes() {
    }

    public static void register(Router router, InvoiceService invoices, AuthGuard guard) {
        router.get("/invoices", guard.staffOnly((request, staff) -> {
            int limit = request.queryParam("limit", Integer::parseInt).orElse(InvoiceService.DEFAULT_LIMIT);
            return invoices.listRecent(limit);
        }));
    }
}
