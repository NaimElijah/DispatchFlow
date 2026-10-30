package com.dispatchflow.example.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Example: an identity as a value object. The domain assigns it when a widget is created, so a
 * widget has an identity before it is ever stored.
 *
 * @param value the identity's value
 */
public record WidgetId(UUID value) {

    /**
     * Checks that the identity has a value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public WidgetId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Creates a new, unique identity.
     *
     * @return the identity
     */
    public static WidgetId newId() {
        return new WidgetId(UUID.randomUUID());
    }
}
