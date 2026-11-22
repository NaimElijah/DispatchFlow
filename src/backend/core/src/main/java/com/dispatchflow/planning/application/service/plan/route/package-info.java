/**
 * The route use cases of Planning's {@code Plan} aggregate (DL §4.4, §17): locking and unlocking a
 * route, handing a route over to another driver with the roster following it, the route re-check
 * job, and one route's screens. A route belongs to its plan, so this package sits beneath the
 * plan's use cases (ADR-125).
 */
@NullMarked
package com.dispatchflow.planning.application.service.plan.route;

import org.jspecify.annotations.NullMarked;
