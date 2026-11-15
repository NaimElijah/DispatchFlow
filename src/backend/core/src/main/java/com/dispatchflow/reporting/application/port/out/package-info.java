/**
 * Reporting's outbound ports (DL §3.6, §4.7, §13; TS §6.4): the {@link
 * com.dispatchflow.reporting.application.port.out.BaselineRepository}, the {@link
 * com.dispatchflow.reporting.application.port.out.BaselinePlanner} that asks the optimizer for a
 * baseline with its {@link com.dispatchflow.reporting.application.port.out.BaselineRequest}, and the
 * read port {@link com.dispatchflow.reporting.application.port.out.ReportingViews} over the
 * read-only views other modules publish for Reporting (DL §2.3 rule 3, ADR-041, ADR-109), with the
 * records it answers with and the view records the summary queries answer with.
 *
 * <p>Adapters implement every port here. Reporting keeps no personal data (ADR-109); a view that
 * shows a customer's name is personal data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.reporting.application.port.out;

import org.jspecify.annotations.NullMarked;
