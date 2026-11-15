/**
 * Fleet's published API (DL §2.2, §2.3 rule 4): {@link
 * com.dispatchflow.fleet.application.api.FleetFacts}, the register and the roster as Planning and
 * Live Operations read them, and {@link com.dispatchflow.fleet.application.api.MoveVehicleWithRoute},
 * which Planning calls after a hand-over.
 *
 * <p>Its types use only kernel types, JDK types, their own records, and Audit's {@code
 * CallContext}, never a domain type (DL §2.3 rule 1). The caller translates each answer into its
 * own values.
 */
@NullMarked
package com.dispatchflow.fleet.application.api;

import org.jspecify.annotations.NullMarked;
