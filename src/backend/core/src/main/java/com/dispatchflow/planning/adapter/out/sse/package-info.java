/**
 * Planning's live-updates adapter (ADR-130): implements {@code LiveUpdates} over the event stream,
 * whose endpoint and registry belong to the app-wide {@code web} package (ADR-040).
 */
@NullMarked
package com.dispatchflow.planning.adapter.out.sse;

import org.jspecify.annotations.NullMarked;
