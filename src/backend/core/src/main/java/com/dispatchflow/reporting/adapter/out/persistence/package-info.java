/**
 * Reporting's persistence adapter (ADR-126, ADR-127): the JPA implementation of the {@code
 * BaselineRepository} over the tables of the {@code reporting} schema, with the auto-applied
 * converters of the baseline's two single-column figures.
 *
 * <p>A plan is settled once, by a baseline or by the optimizer's refusal of one, and never by both
 * (FR-39). Each settled plan has one row in {@code reporting.baseline_outcome}, which also keeps a
 * refusal's cause, and a baseline's row refers to that row as a computed one, so the database
 * itself refuses a second settlement and a baseline beside a refusal.
 *
 * <p>The baseline's multi-column values ({@code Withdrawal}, and its planned orders' kernel windows
 * and starts) are built by their canonical constructors or by the kernel adapter's instantiators, so
 * no instantiator is registered here. Like every Reporting class, this package uses no other
 * module's classes but their {@code events} (ADR-120 P5).
 */
@NullMarked
package com.dispatchflow.reporting.adapter.out.persistence;

import org.jspecify.annotations.NullMarked;
