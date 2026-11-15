/**
 * Planning's published API (DL §2.2, §2.3 rule 4): what Live Operations asks about the routes in
 * force and their history ({@link com.dispatchflow.planning.application.api.RouteMembership},
 * {@link com.dispatchflow.planning.application.api.PublishedRoutes}, {@link
 * com.dispatchflow.planning.application.api.PlanningSettingsFacts}) and the changes it asks
 * Planning to make ({@link com.dispatchflow.planning.application.api.ReconcileLateOutcome}, {@link
 * com.dispatchflow.planning.application.api.RemoveSupersededPutBack}). Reporting reads no Java API
 * of Planning's: it reads the views Planning publishes (ADR-041, ADR-109).
 *
 * <p>Its types use only kernel types, JDK types, their own records, enums and sealed interfaces,
 * and Audit's {@code CallContext}, never a domain type (DL §2.3 rule 1). A revision travels as its
 * number, an {@code int} from 1, which Live Operations keeps as its own {@code RevisionNo}. A
 * planned time travels as an {@link java.time.Instant}, because a route that breaks a time rule may
 * be planned past its date's end (ADR-112). Planning holds no order's window or door flag, so
 * those come from Customers &amp; Orders' {@code OrdersForDate}.
 */
@NullMarked
package com.dispatchflow.planning.application.api;

import org.jspecify.annotations.NullMarked;
