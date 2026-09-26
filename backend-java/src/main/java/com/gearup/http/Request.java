package com.gearup.http;

import com.gearup.exception.BadRequestException;
import com.sun.net.httpserver.Headers;

import java.util.Map;
import java.util.Optional;

/**
 * The parts of an HTTP request that route handlers need, already parsed.
 *
 * <p>A new {@code Request} is created for every incoming call and is only used by the one
 * worker thread handling it, so nothing here is shared between requests (Factor VI).
 */
public final class Request {

    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;
    private final Headers headers;
    private final String body;

    Request(String method, String path, Map<String, String> pathParams,
            Map<String, String> queryParams, Headers headers, String body) {
        this.method = method;
        this.path = path;
        this.pathParams = Map.copyOf(pathParams);
        this.queryParams = Map.copyOf(queryParams);
        this.headers = headers;
        this.body = body;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    /** A value taken from the URL path, e.g. {@code id} in {@code /cars/{id}}. */
    public String pathParam(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Route has no path parameter named '" + name + "'");
        }
        return value;
    }

    /** A value from the query string, e.g. {@code ?category=small}. Empty values count as missing. */
    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(queryParams.get(name)).filter(value -> !value.isBlank());
    }

    /** A request header such as {@code Authorization}. Header names are not case-sensitive. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.getFirst(name));
    }

    /**
     * Parses the JSON body into the given type, for example {@code request.bodyAs(LoginRequest.class)}.
     *
     * @throws BadRequestException if the body is missing or not valid JSON
     */
    public <T> T bodyAs(Class<T> type) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("Request body is required");
        }
        return Json.fromJson(body, type);
    }
}
