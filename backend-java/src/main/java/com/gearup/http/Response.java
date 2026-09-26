package com.gearup.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A finished HTTP response: status code, content type and body bytes.
 */
public record Response(int status, String contentType, byte[] body) {

    private static final String JSON_TYPE = "application/json; charset=utf-8";

    /** A response whose body is the given object written as JSON. */
    public static Response json(int status, Object value) {
        return new Response(status, JSON_TYPE, Json.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    /** An error response in the same shape FastAPI used: {@code {"detail": "..."}}. */
    public static Response error(int status, String message) {
        return json(status, Map.of("detail", message));
    }
}
