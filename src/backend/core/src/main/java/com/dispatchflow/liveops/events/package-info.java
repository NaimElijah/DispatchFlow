/**
 * The Live Operations context's published events (DL §7.3): past-tense records that carry only
 * kernel types, {@code Instant}s, numbers and this package's own enums, never a reason's text, a
 * proof or a note: {@link com.dispatchflow.liveops.events.RouteStarted}, {@link
 * com.dispatchflow.liveops.events.OutcomeRecorded} and {@link
 * com.dispatchflow.liveops.events.PutBackSuperseded} (ADR-107). A correction raises no event of its
 * own, because nothing reacts to one (FR-374, ADR-064).
 */
@NullMarked
package com.dispatchflow.liveops.events;

import org.jspecify.annotations.NullMarked;
