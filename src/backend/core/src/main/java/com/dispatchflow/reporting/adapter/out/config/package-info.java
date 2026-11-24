/**
 * Reporting's configuration adapter (ADR-121 decision 1, ADR-131): implements the port of
 * Reporting's own that reads the scoring rates its baseline needs, bound to the same configuration
 * as Planning's {@code SolveConfiguration}. The port is not declared yet; it comes with the
 * baseline's travel inputs.
 */
@NullMarked
package com.dispatchflow.reporting.adapter.out.config;

import org.jspecify.annotations.NullMarked;
