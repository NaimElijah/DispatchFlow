/**
 * Live Operations' outbound ports (DL §13, TS §6.4): the repositories of {@code Delivery}, {@code
 * RouteRun} and {@code Alert}; the position log; the push notifier and the live updates; the phone's idempotency record, {@link
 * com.dispatchflow.liveops.application.port.out.ReportReceipts} (ADR-087, placement P2); and the read
 * port {@link com.dispatchflow.liveops.application.port.out.LiveOpsViews} with the view records the
 * screens show. The adapters implement them. The proof store, with its file and its refusals, is
 * the subpackage {@code proof}, so this package stays within the 30 files of ADR-118's rule; this
 * root imports nothing from it.
 *
 * <p>A record here that holds a position, a proof, a reason or a recipient's details is personal
 * data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.liveops.application.port.out;

import org.jspecify.annotations.NullMarked;
