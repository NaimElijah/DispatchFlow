/**
 * Live Operations' live-updates adapter (ADR-130): implements {@code LiveUpdates} over the event
 * stream, filtered by role. The stream's endpoint and its registry of open streams are the app-wide
 * {@code web} ADR's to place (ADR-040, ADR-130 decision 6).
 */
@NullMarked
package com.dispatchflow.liveops.adapter.out.sse;

import org.jspecify.annotations.NullMarked;
