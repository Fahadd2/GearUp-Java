package com.gearup.http;

import com.gearup.exception.ApiException;
import com.gearup.exception.NotFoundException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends each HTTP request to the matching route handler, and is the single place where
 * exceptions are caught and turned into responses (Factor IX).
 *
 * <p>Routes are matched in the order they are registered. Path templates may contain
 * parameters in braces, e.g. {@code /cars/{id}}.
 */
public final class Router implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(Router.class);
    private static final Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)}");

    private record Route(String method, Pattern pattern, List<String> paramNames, RouteHandler handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public void get(String pathTemplate, RouteHandler handler) {
        add("GET", pathTemplate, handler);
    }

    public void post(String pathTemplate, RouteHandler handler) {
        add("POST", pathTemplate, handler);
    }

    public void put(String pathTemplate, RouteHandler handler) {
        add("PUT", pathTemplate, handler);
    }

    /** Turns a template like {@code /cars/{id}} into the regex {@code /cars/([^/]+)}. */
    private void add(String method, String pathTemplate, RouteHandler handler) {
        List<String> paramNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder();
        Matcher matcher = PATH_PARAM.matcher(pathTemplate);
        int end = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(pathTemplate.substring(end, matcher.start())));
            regex.append("([^/]+)");
            paramNames.add(matcher.group(1));
            end = matcher.end();
        }
        regex.append(Pattern.quote(pathTemplate.substring(end)));
        routes.add(new Route(method, Pattern.compile(regex.toString()), List.copyOf(paramNames), handler));
    }

    /** Called by the HttpServer on one of the worker threads for every request. */
    @Override
    public void handle(HttpExchange exchange) {
        long startNanos = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        Response response;
        try {
            response = dispatch(exchange, method, path);
        } catch (ApiException e) {
            // Expected errors (bad input, not found, not logged in...): tell the client why.
            response = Response.error(e.status(), e.getMessage());
        } catch (Exception e) {
            // Unexpected errors: log the full stack trace, but do not leak details to the client.
            log.error("Unhandled error while processing {} {}", method, path, e);
            response = Response.error(500, "Internal server error");
        }

        try {
            send(exchange, response);
        } catch (IOException e) {
            log.warn("Could not send response for {} {}: {}", method, path, e.getMessage());
        } finally {
            exchange.close();
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("{} {} -> {} ({} ms)", method, path, response.status(), elapsedMs);
    }

    private Response dispatch(HttpExchange exchange, String method, String path) throws Exception {
        boolean pathExists = false;
        for (Route route : routes) {
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            pathExists = true;
            if (!route.method().equals(method)) {
                continue;
            }
            Request request = new Request(method, path, pathParams(route, matcher),
                    queryParams(exchange.getRequestURI().getRawQuery()),
                    exchange.getRequestHeaders(), readBody(exchange));
            return Response.json(200, route.handler().handle(request));
        }

        if (pathExists) {
            throw new ApiException(405, "Method " + method + " not allowed on " + path);
        }
        throw new NotFoundException("No endpoint at " + path);
    }

    private static Map<String, String> pathParams(Route route, Matcher matcher) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < route.paramNames().size(); i++) {
            params.put(route.paramNames().get(i), matcher.group(i + 1));
        }
        return params;
    }

    /** Parses {@code a=1&b=two} into a map. If a name repeats, the first value wins. */
    private static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(name, value);
        }
        return params;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        exchange.sendResponseHeaders(response.status(), response.body().length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response.body());
        }
    }
}
