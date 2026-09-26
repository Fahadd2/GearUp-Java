package com.gearup.api;

import com.gearup.http.Router;

/**
 * {@code GET /health}: lets a load balancer or the developer check that the process is up.
 */
public final class HealthRoutes {

    record HealthStatus(String status, String message) {
    }

    private HealthRoutes() {
    }

    public static void register(Router router) {
        router.get("/health", request -> new HealthStatus("ok", "API is running"));
    }
}
