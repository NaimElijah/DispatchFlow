/**
 * Reporting's use cases (DL §4.7, §13; TS §6.2): the listeners that compute and recompute a
 * published plan's baseline ({@code ComputeBaseline}, {@code RecomputeBaseline}), and the query
 * class of the daily summary and the baseline comparison ({@code SummaryQueries}). Reporting has no
 * API of its own and raises no event (DL §7.2). It reads other modules only through its own read
 * port, {@code ReportingViews}, over the views they publish, and calls none of their Java APIs
 * (ADR-041, ADR-109).
 *
 * <p>A listener solves the baseline before its transaction opens, then stores it in one
 * transaction (DL §6.3). Each body throws until the milestone its Javadoc names builds it.
 */
@NullMarked
package com.dispatchflow.reporting.application.service;

import org.jspecify.annotations.NullMarked;
