/**
 * The use cases of Planning's {@code Plan} aggregate (DL §4.4, §6.2, §9.1): opening, solving, editing,
 * publishing, handing over and cancelling a plan; the listeners that change it after a solve or an
 * order's event; the route re-check job; the queries of the planning screens; and the
 * implementations of Planning's {@code api} and of the {@code spi} that Customers &amp; Orders and
 * Fleet own, which read the plans (DL §2.1's dashed arrows).
 *
 * <p>An edit asks the optimizer to judge the plan as the edit would leave it, through the plan's
 * {@code after…} query, before its transaction opens; the command then leaves exactly that layout (DL
 * §6.3, ADR-112).
 */
@NullMarked
package com.dispatchflow.planning.application.service.plan;

import org.jspecify.annotations.NullMarked;
