/**
 * Recipient Messaging's persistence adapter (ADR-126, ADR-127): the JPA implementations of the
 * repositories of {@code StopMessages}, {@code RatingRequest} and {@code MessagingSettings}, of the
 * reply log and of the opt-out history, over the tables of the {@code messaging} schema. It holds
 * the auto-applied converters of Messaging's single-column values, and the {@code @Immutable}
 * entities of the two records, which the record types themselves can't be (ADR-127 decision 6).
 *
 * <p>Messaging's multi-column values ({@code Rated}, {@code ReceivedComment}) can't refuse a stored
 * value, and the migration's {@code NOT NULL} and {@code CHECK} constraints keep their columns
 * whole, so Hibernate builds them through their canonical constructors and no instantiator is
 * registered here.
 */
@NullMarked
package com.dispatchflow.messaging.adapter.out.persistence;

import org.jspecify.annotations.NullMarked;
