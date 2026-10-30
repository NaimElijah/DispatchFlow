package com.dispatchflow.example.api;

/**
 * Example: thrown through {@link ExampleApi} when a request breaks one of the context's rules. It
 * lives in {@code api}, so a caller handles a rejection without importing the context's domain.
 */
public final class WidgetRejectedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message which rule was broken, and by what value
     * @param cause the domain's own exception
     */
    public WidgetRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
