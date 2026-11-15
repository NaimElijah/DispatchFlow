/**
 * The use cases of a route being driven (DL §4.5, §6.2): starting and finishing it, its positions,
 * the driver's <em>Seen</em> on a route change, and the listeners that tell drivers their routes are
 * ready, changed or cancelled. It also holds the dispatcher's live queries and the driver's day,
 * which read routes, runs and stops together (placement P6).
 */
@NullMarked
package com.dispatchflow.liveops.application.service.routerun;

import org.jspecify.annotations.NullMarked;
