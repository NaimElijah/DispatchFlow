package com.dispatchflow.example.api;

/**
 * Example: a request to create a widget. The values are not checked here: the domain enforces the
 * rules.
 *
 * @param width the widget's width
 * @param height the widget's height
 */
public record CreateWidgetCommand(int width, int height) {}
