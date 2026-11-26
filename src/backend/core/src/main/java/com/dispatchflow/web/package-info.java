/**
 * The web concerns of the whole application rather than of one module (ADR-140). This package
 * holds the trace-ID filter, which also hands an exception that escaped every handler to Tomcat's
 * error dispatch and refuses with 400 a path Spring can't parse, the call wrapper that retries a
 * call that lost a race and binds the call's holders, the handler of requests that
 * break the contract and of Tomcat's refusals of their parameters, the handler of the kernel's
 * errors and {@code UnexpectedErrorHandler}, which answers an exception nothing else handled, and
 * {@code ErrorEndpoint}, which answers every error Tomcat dispatches to the error path, with the
 * served JSON converter's strictness and the request body's size limit. {@code web.security} holds
 * who the caller is, {@code web.stream} will hold the event stream, and {@code web.e2e} the
 * end-to-end tests' controls.
 *
 * <p>Nothing depends on {@code web}: it sits above the modules, and uses of them only their {@code
 * application.api} and {@code events}, besides the kernel and the frameworks. What a module must
 * reach sits below it, beside the kernel, in {@code shared.adapter.in.web} and {@code
 * shared.adapter.out.sse}. {@code ApplicationRules} rules 2, 11 and 13 enforce this.
 */
@NullMarked
package com.dispatchflow.web;

import org.jspecify.annotations.NullMarked;
