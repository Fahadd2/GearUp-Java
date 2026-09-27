package com.gearup.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * An enum whose values have a fixed text label, used both in the database and in the JSON API.
 *
 * <p>Java constants follow Java naming ({@code AVAILABLE}), while the database and the existing
 * frontend use labels such as {@code "Available"} or {@code "small"}. The label is the only form
 * that leaves the Java code: {@code Transaction} binds it as the SQL parameter and {@code Json}
 * writes it to responses.
 */
public interface LabeledEnum {

    String label();

    /**
     * Finds the constant with the given label, for example
     * {@code LabeledEnum.fromLabel(CarStatus.class, "Available")}.
     *
     * @throws IllegalArgumentException if no constant has that label; the message lists the allowed labels
     */
    static <E extends Enum<E> & LabeledEnum> E fromLabel(Class<E> type, String label) {
        for (E constant : type.getEnumConstants()) {
            if (constant.label().equals(label)) {
                return constant;
            }
        }
        String allowed = Arrays.stream(type.getEnumConstants())
                .map(LabeledEnum::label)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException("'" + label + "' is not one of: " + allowed);
    }
}
