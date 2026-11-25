package com.dispatchflow.shared.adapter.in.web;

import org.springframework.core.Ordered;

/**
 * The order of the core's exception handlers, for their {@code @Order}: module, contract, kernel,
 * unexpected (ADR-142). Spring's {@code ExceptionHandlerExceptionResolver} asks the advices in this
 * order, and each advice matches the thrown type and then any of its causes before the next advice
 * is asked; the first that matches answers. When the handler it picked throws, no later advice
 * runs, and {@code ResponseEntityExceptionHandler.handleException} rethrows any thrown type it
 * doesn't know, even when it matched one of its causes. So:
 *
 * <ul>
 *   <li>a module's handler comes first, so a module's own exception that wraps a Spring MVC
 *       exception, such as an optimizer outage wrapping a {@code RestClientException} with a
 *       {@code HttpMessageNotReadableException} cause, reaches its module's handler;
 *   <li>the contract's handler comes before the kernel's, so a converter that wraps a {@code
 *       RuleViolation} in a {@code MethodArgumentTypeMismatchException} answers 400, not 422;
 *   <li>{@code web}'s handler of the unexpected comes last, and an exception that escapes all four,
 *       or one thrown outside Spring MVC, reaches {@code web}'s error endpoint.
 * </ul>
 *
 * <p>It sits in the kernel's adapter, not in {@code web}, because a module's handler can't depend
 * on {@code web} ({@code ApplicationRules} rule 2).
 */
public final class HandlerOrder {

    /** Each module's handler of its own application exceptions, in its {@code adapter.in.web}. */
    public static final int MODULE = Ordered.LOWEST_PRECEDENCE - 3;

    /** {@code web}'s handler of requests that break the contract, and of Spring MVC's own errors. */
    public static final int CONTRACT = Ordered.LOWEST_PRECEDENCE - 2;

    /** {@code web}'s handler of the kernel's errors. */
    public static final int KERNEL = Ordered.LOWEST_PRECEDENCE - 1;

    /** {@code web}'s handler of any other exception thrown by a handler. */
    public static final int UNEXPECTED = Ordered.LOWEST_PRECEDENCE;

    private HandlerOrder() {}
}
