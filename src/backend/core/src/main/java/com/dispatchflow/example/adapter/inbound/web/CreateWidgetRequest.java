package com.dispatchflow.example.adapter.inbound.web;

import jakarta.validation.constraints.NotNull;

/**
 * Example: a request body. The boxed types let a missing field be told apart from a zero: this
 * adapter rejects a missing one, and the domain rejects a zero.
 *
 * @param width the widget's width
 * @param height the widget's height
 */
record CreateWidgetRequest(@NotNull Integer width, @NotNull Integer height) {}
