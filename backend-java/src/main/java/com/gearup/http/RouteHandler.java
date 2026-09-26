package com.gearup.http;

/**
 * Handles one API route. Routes are registered as lambdas, for example:
 *
 * <pre>{@code
 * router.get("/health", request -> new HealthStatus("ok", "API is running"));
 * }</pre>
 *
 * <p>The returned object is sent to the client as JSON with status 200. To report an error, the
 * handler (or the service it calls) throws an {@link com.gearup.exception.ApiException}.
 */
@FunctionalInterface
public interface RouteHandler {

    Object handle(Request request) throws Exception;
}
