package com.dispatchflow.example.domain;

import java.util.Objects;

/**
 * Example: the root of an aggregate. A widget always has an identity and a valid size, because
 * nothing can create one without them.
 */
public final class Widget {

    private final WidgetId id;
    private final Size size;

    private Widget(WidgetId id, Size size) {
        this.id = Objects.requireNonNull(id, "id");
        this.size = Objects.requireNonNull(size, "size");
    }

    /**
     * Creates a new widget, giving it a new identity.
     *
     * @param size the widget's size
     * @return the new widget
     */
    public static Widget create(Size size) {
        return new Widget(WidgetId.newId(), size);
    }

    /**
     * Rebuilds a widget that was created before, for example when it is loaded from storage.
     *
     * @param id the widget's existing identity
     * @param size the widget's size
     * @return the widget
     */
    public static Widget restore(WidgetId id, Size size) {
        return new Widget(id, size);
    }

    /**
     * Returns the widget's identity.
     *
     * @return the identity
     */
    public WidgetId id() {
        return id;
    }

    /**
     * Returns the widget's size.
     *
     * @return the size
     */
    public Size size() {
        return size;
    }
}
