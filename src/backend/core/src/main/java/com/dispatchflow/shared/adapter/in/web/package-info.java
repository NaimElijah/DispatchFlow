/**
 * The web code every module's controllers share (ADR-043, ADR-140). It holds {@link
 * com.dispatchflow.shared.adapter.in.web.Problems}, the one factory of every error body, with the
 * MDC key {@code traceId} it reads the request's trace ID from and the cap on a 400's items, {@link
 * com.dispatchflow.shared.adapter.in.web.TraceIds}, which makes a fresh trace ID, and {@link
 * com.dispatchflow.shared.adapter.in.web.HandlerOrder}, the order of every exception handler, and
 * {@link com.dispatchflow.shared.adapter.in.web.FilterOrder}, the order of the core's own filters. It
 * will hold the code generated from {@code client-api}, each module's {@code <Tag>Api} interface in
 * {@code .api} and the models in {@code .model}, generated at every build and never committed
 * (#161), and {@code @RequiresCapability}, which the generated interfaces carry (ADR-068, #171).
 * It sits beside the kernel, below {@code web}, so every module's {@code in/web} controller and
 * handler use it without depending on {@code web}. Its types stop at those controllers and {@code
 * web}, and never reach a use case.
 */
@NullMarked
package com.dispatchflow.shared.adapter.in.web;

import org.jspecify.annotations.NullMarked;
