/**
 * Reporting's optimizer adapter (ADR-130): implements {@code BaselinePlanner} over the shared
 * optimizer client. It is Reporting's own: no adapter uses another module's, and Reporting uses no
 * Planning class but its events (ADR-120 P5).
 */
@NullMarked
package com.dispatchflow.reporting.adapter.out.optimizer;

import org.jspecify.annotations.NullMarked;
