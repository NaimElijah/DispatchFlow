package com.dispatchflow.web.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A call under {@code /api/v1} came with no principal, no credential or one that failed, to an
 * operation that isn't open or to a handler that is no operation. {@link NotSignedInHandler}
 * answers it 401 for an annotated handler method, a router function and the static-resource
 * handler, which are all Spring MVC lets its advice see. For any other handler type, such as an
 * {@code HttpRequestHandler} bean, Spring answers {@code sendError(401)} from the status below, and
 * the error endpoint answers that with the same code and challenge (ADR-148). Its message names no
 * value.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
final class NotSignedIn extends RuntimeException {

    private static final String MESSAGE = "A call under /api/v1 came with no principal";

    NotSignedIn() {
        super(MESSAGE, null, false, false);
    }
}
