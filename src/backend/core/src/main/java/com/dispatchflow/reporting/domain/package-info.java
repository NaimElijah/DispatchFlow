/**
 * The Reporting context's domain (DL §4.7): the baseline each published plan is compared with
 * (FR-39), and the on-time rates of the daily summary and the comparison (FR-264, FR-266), with
 * their values and named errors ({@link com.dispatchflow.reporting.domain.ReportingError}). ADR-109
 * records the readings no document settled.
 *
 * <p>The {@code Baseline} aggregate, with the figures and withdrawals it compares, sits in {@code
 * baseline} (ADR-125). This package holds the named errors, the on-time rates and the planned and
 * actual stops they count, which the baseline uses too; it imports nothing from {@code baseline}.
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify and the shared kernel, and takes {@code
 * now} and every fact from another context as its own values (DL §2.3, rule 1). Reporting raises no
 * event (DL §7.2), so it has no {@code events} package.
 */
@NullMarked
package com.dispatchflow.reporting.domain;

import org.jspecify.annotations.NullMarked;
