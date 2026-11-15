/**
 * The Fleet context's published events (DL §7.3): past-tense records that carry only kernel types,
 * {@code Instant}s and this package's own enums, never a person's name or phone. Today it holds
 * {@link com.dispatchflow.fleet.events.DriverRetired} (ADR-103).
 */
@NullMarked
package com.dispatchflow.fleet.events;

import org.jspecify.annotations.NullMarked;
