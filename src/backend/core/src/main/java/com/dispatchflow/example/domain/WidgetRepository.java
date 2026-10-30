package com.dispatchflow.example.domain;

import java.util.Optional;

/**
 * Example: an outbound port. The domain declares it in its own terms, and a persistence adapter
 * implements it.
 */
public interface WidgetRepository {

    /**
     * Stores a widget, replacing any stored widget with the same identity.
     *
     * @param widget the widget to store
     */
    void save(Widget widget);

    /**
     * Loads a widget.
     *
     * @param id the widget's identity
     * @return the widget, or empty if none has that identity
     */
    Optional<Widget> findById(WidgetId id);
}
