package com.dispatchflow.example.domain;

/** Example: thrown when a widget would break a domain rule, such as a size that is not positive. */
public final class InvalidWidgetException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message which rule was broken, and by what value
     */
    public InvalidWidgetException(String message) {
        super(message);
    }
}
