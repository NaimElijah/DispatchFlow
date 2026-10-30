package com.dispatchflow.example.domain;

/**
 * Example: a value object that guards an invariant. Both dimensions are positive, so an invalid
 * size cannot exist.
 *
 * @param width the width
 * @param height the height
 */
public record Size(int width, int height) {

    /**
     * Checks both dimensions.
     *
     * @throws InvalidWidgetException if either dimension is zero or negative
     */
    public Size {
        if (width <= 0) {
            throw new InvalidWidgetException("width must be positive, was " + width);
        }
        if (height <= 0) {
            throw new InvalidWidgetException("height must be positive, was " + height);
        }
    }
}
