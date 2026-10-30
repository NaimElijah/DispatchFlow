package com.dispatchflow.example.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Example: what the rest of the system may ask of this context. Other contexts and this context's
 * own inbound adapters depend on this interface, never on the class that implements it.
 */
public interface ExampleApi {

    /**
     * Creates a widget.
     *
     * @param command the widget's size
     * @return the created widget, with its new identity
     * @throws WidgetRejectedException if a dimension is not positive
     */
    WidgetView create(CreateWidgetCommand command);

    /**
     * Finds a widget.
     *
     * @param widgetId the widget's identity
     * @return the widget, or empty if no widget has that identity
     */
    Optional<WidgetView> find(UUID widgetId);
}
