/**
 * The persistence adapter of {@code Plan} (ADR-126, ADR-127): the JPA implementation of {@code
 * PlanRepository} over the plan's tables in the {@code planning} schema, and the {@code @Immutable}
 * entities a plan's revisions are appended as, which no plan load reads (ADR-059). Planning's
 * converters, in the parent package, are auto-applied here too.
 */
@NullMarked
package com.dispatchflow.planning.adapter.out.persistence.plan;

import org.jspecify.annotations.NullMarked;
