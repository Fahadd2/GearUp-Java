package com.gearup.http;

import com.gearup.exception.BadRequestException;
import com.gearup.model.LabeledEnum;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * Converts between Java objects and JSON.
 *
 * <p>Java fields use camelCase ({@code pricePerDay}) while the API uses snake_case
 * ({@code price_per_day}), matching the Python backend so the existing frontend keeps working.
 * Dates are written as ISO strings such as {@code "2025-11-02"}, and enums as their label
 * (see {@link LabeledEnum}).
 *
 * <p>A single {@link Gson} instance is shared by all worker threads; Gson is thread-safe.
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(LocalDate.class, isoAdapter(LocalDate::parse))
            .registerTypeAdapter(OffsetDateTime.class, isoAdapter(OffsetDateTime::parse))
            .registerTypeAdapterFactory(new LabeledEnumAdapterFactory())
            .serializeNulls()
            .disableHtmlEscaping() // responses are read with fetch(), not pasted into HTML
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

    /**
     * Reads and writes a java.time value as its ISO text, e.g. "2025-11-02".
     * {@code parser} is a method reference such as {@code LocalDate::parse}.
     */
    private static <T> TypeAdapter<T> isoAdapter(Function<String, T> parser) {
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public T read(JsonReader in) throws IOException {
                String text = in.nextString();
                try {
                    return parser.apply(text);
                } catch (DateTimeParseException e) {
                    throw new JsonParseException("Invalid date '" + text + "', expected ISO format such as 2025-11-02");
                }
            }
        }.nullSafe();
    }

    /** Makes Gson write and read every {@link LabeledEnum} by its label instead of its Java name. */
    private static final class LabeledEnumAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            Class<? super T> type = typeToken.getRawType();
            if (!type.isEnum() || !LabeledEnum.class.isAssignableFrom(type)) {
                return null; // not ours: let Gson handle it
            }
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    out.value(((LabeledEnum) value).label());
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    String label = in.nextString();
                    for (Object constant : type.getEnumConstants()) {
                        if (((LabeledEnum) constant).label().equals(label)) {
                            @SuppressWarnings("unchecked") // constant is one of T's own enum values
                            T value = (T) constant;
                            return value;
                        }
                    }
                    throw new JsonParseException("'" + label + "' is not a valid " + type.getSimpleName());
                }
            }.nullSafe();
        }
    }
}
