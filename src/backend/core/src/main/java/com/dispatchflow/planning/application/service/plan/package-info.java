/**
 * The use cases of Planning's {@code Plan} aggregate (DL §4.4, §6.2, §9.1): opening, solving,
 * publishing, handing over and cancelling a plan; the listeners that change it after a solve or an
 * order's event; the route re-check job; the queries of the planning screens; and the
 * implementations of Planning's {@code api} and of the {@code spi} that Customers &amp; Orders and
 * Fleet own, which read the plans (DL §2.1's dashed arrows). The edits of a plan sit in {@code
 * edit}, beneath it (ADR-125); this package imports none of them.
 */
@NullMarked
package com.dispatchflow.planning.application.service.plan;

import org.jspecify.annotations.NullMarked;
