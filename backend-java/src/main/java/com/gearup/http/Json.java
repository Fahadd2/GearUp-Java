package com.gearup.http;

import com.gearup.exception.BadRequestException;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Converts between Java objects and JSON.
 *
 * <p>Java fields use camelCase ({@code pricePerDay}) while the API uses snake_case
 * ({@code price_per_day}), matching the Python backend so the existing frontend keeps working.
 * Dates are written as ISO strings such as {@code "2025-11-02"}.
 *
 * <p>A single {@link Gson} instance is shared by all worker threads; Gson is thread-safe.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
            .serializeNulls()
            .create();

    private Json() {
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /**
     * Parses a request body into the given type.
     *
     * @throws BadRequestException if the body is not valid JSON for that type
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new BadRequestException("Request body is required");
            }
            return value;
        } catch (JsonParseException e) {
            throw new BadRequestException("Invalid JSON body: " + e.getMessage());
        }
    }

    /** Reads and writes {@link LocalDate} as "YYYY-MM-DD". */
    private static final class LocalDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate date) throws IOException {
            out.value(date.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            String text = in.nextString();
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Invalid date '" + text + "', expected YYYY-MM-DD");
            }
        }
    }
}
