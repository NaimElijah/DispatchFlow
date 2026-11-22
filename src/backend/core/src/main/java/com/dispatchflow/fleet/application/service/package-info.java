/**
 * Fleet's use cases (DL §4.2): changing the register, the driver records and the day rosters, the
 * listeners on {@code DriverRetired} and Access's {@code AccountAnonymized}, the driver retention
 * job, the queries, and the implementations of the {@code FleetFacts} and {@code
 * MoveVehicleWithRoute} APIs. Each aggregate's sit in its own package, {@code driver}, {@code
 * register} and {@code roster} (ADR-125). This package holds the two that read all three: the
 * implementation of {@code FleetFacts} and the register and drivers screens' queries.
 *
 * <p>A changing use case reads Access's {@code Accounts} and Planning's {@code RecordUsage} before
 * it changes its one aggregate (DL §6.3).
 */
@NullMarked
package com.dispatchflow.fleet.application.service;

import org.jspecify.annotations.NullMarked;
