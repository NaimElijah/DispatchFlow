/**
 * Reporting's {@code Baseline} aggregate (DL §4.7, §17; FR-39): the nearest-neighbour plan a
 * published plan is compared with, why it was last computed, the comparison, the planned figures
 * it compares on (distance, driver-hours and each order's service), where the plan stands, and how
 * the vehicles withdrawn mid-route are treated (FR-271). It uses the root's on-time rates and
 * planned orders, one way (ADR-125).
 */
@NullMarked
package com.dispatchflow.reporting.domain.baseline;

import org.jspecify.annotations.NullMarked;
