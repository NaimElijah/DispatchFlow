/**
 * Reporting's optimizer client (ADR-130): implements {@code BaselinePlanner} over {@code
 * optimization-api} v1. It is Reporting's own: no adapter uses another module's, and Reporting uses
 * no Planning class (ADR-120 P5).
 */
@NullMarked
package com.dispatchflow.reporting.adapter.out.optimizer;

import org.jspecify.annotations.NullMarked;
