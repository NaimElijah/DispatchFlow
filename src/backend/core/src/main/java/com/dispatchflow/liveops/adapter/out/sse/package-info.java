/**
 * Live Operations' live-updates adapter (ADR-130): implements {@code LiveUpdates} over the event
 * stream, filtered by role, whose endpoint and registry belong to the app-wide {@code web} package
 * (ADR-040).
 */
@NullMarked
package com.dispatchflow.liveops.adapter.out.sse;

import org.jspecify.annotations.NullMarked;
