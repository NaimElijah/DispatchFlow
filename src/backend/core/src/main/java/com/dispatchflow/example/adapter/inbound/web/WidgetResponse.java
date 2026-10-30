package com.dispatchflow.example.adapter.inbound.web;

import com.dispatchflow.example.api.WidgetView;
import java.util.UUID;

/**
 * Example: a response body, the {@code ExampleWidget} schema of the client API. It is kept apart
 * from {@link WidgetView}, so the public contract can change without changing what other contexts
 * see, and the reverse.
 *
 * @param id the widget's identity
 * @param width the widget's width
 * @param height the widget's height
 */
record WidgetResponse(UUID id, int width, int height) {

    static WidgetResponse from(WidgetView widget) {
        return new WidgetResponse(widget.id(), widget.width(), widget.height());
    }
}
