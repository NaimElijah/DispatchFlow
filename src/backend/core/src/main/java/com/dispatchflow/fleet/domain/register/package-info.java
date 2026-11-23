/**
 * The fleet register aggregate (DL §4.2): {@link com.dispatchflow.fleet.domain.register.FleetRegister}
 * with its depots and vehicles, and the values only they use, such as plates,
 * capacities and depot hours. The register, its depots and its vehicles share this package because
 * the register reaches its children through package-private members (ADR-118).
 */
@NullMarked
package com.dispatchflow.fleet.domain.register;

import org.jspecify.annotations.NullMarked;
