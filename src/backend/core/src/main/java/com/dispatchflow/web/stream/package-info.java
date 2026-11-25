/**
 * The event stream (ADR-140): its endpoint, written by hand because the code generated from {@code
 * client-api} can't serve a stream, its heartbeat, and the {@code SessionsRevoked} listener that
 * closes the account's streams opened under an older generation (#193, #199). Each stream it opens
 * is registered with {@code shared.adapter.out.sse}.
 */
@NullMarked
package com.dispatchflow.web.stream;

import org.jspecify.annotations.NullMarked;
