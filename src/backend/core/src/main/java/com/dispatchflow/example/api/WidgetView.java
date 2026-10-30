package com.dispatchflow.example.api;

import java.util.UUID;

/**
 * Example: a widget, as the context shows it to the rest of the system.
 *
 * @param id the widget's identity
 * @param width the widget's width
 * @param height the widget's height
 */
public record WidgetView(UUID id, int width, int height) {}
