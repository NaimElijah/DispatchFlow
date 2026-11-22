/**
 * Planning's drops (DL §4.4, §17): an order a plan leaves unserved, with its reason (FR-144), and
 * a vehicle a solve left out, with its cause, which is the fact behind a no-vehicle-available drop
 * (FR-197, ADR-100). Solutions and plans use these values, and they use nothing in either
 * (ADR-125).
 */
@NullMarked
package com.dispatchflow.planning.domain.drop;

import org.jspecify.annotations.NullMarked;
