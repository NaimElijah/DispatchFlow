/**
 * Live Operations' live-updates adapter (ADR-130): implements {@code LiveUpdates} over the event
 * stream, filtered by role. It sends through the registry of open streams in {@code
 * shared.adapter.out.sse}; the stream's endpoint is in {@code web.stream} (ADR-140).
 */
@NullMarked
package com.dispatchflow.liveops.adapter.out.sse;

import org.jspecify.annotations.NullMarked;
