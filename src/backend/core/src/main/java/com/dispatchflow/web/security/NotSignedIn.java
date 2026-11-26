package com.dispatchflow.web.security;

/**
 * A call to an operation that isn't open came with no principal: no credential, or one that
 * failed. {@link NotSignedInHandler} answers it 401. Its message names no value.
 */
final class NotSignedIn extends RuntimeException {

    private static final String MESSAGE = "A call to an operation that isn't open came with no principal";

    NotSignedIn() {
        super(MESSAGE, null, false, false);
    }
}
