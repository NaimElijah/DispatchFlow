package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.ExternalReference;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One uncancelled order of a date, as {@link Orders#uncancelledRowsOn} reads it for the date's
 * capacity: its key and its reference, without loading the order.
 *
 * @param id the order's UUID
 * @param reference its outside reference, or null
 */
record DatedOrderRow(UUID id, @Nullable ExternalReference reference) {}
