package com.dispatchflow.fleet.application.service;

import com.dispatchflow.fleet.application.api.FleetChanges;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.fleet.application.api.RegisterFacts;
import com.dispatchflow.fleet.application.api.RosterFacts;
import com.dispatchflow.fleet.application.api.RosteredDriver;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import java.time.Instant;
import java.util.Optional;

/**
 * Fleet's implementation of {@code FleetFacts}: the register and the rosters as Planning and Live
 * Operations read them, translated from Fleet's own values (DL §2.2, §2.3 rule 1, ADR-056).
 *
 * <p>Neither {@code FleetRegister} nor {@code DayRoster} records when an entry changed, which
 * {@link #changesSince} needs; M2 adds that record.
 *
 * <p>Built in M2.
 */
public final class FleetFactsService implements FleetFacts {

    private final FleetRegisterRepository registers;
    private final DayRosterRepository rosters;

    /**
     * Creates the service.
     *
     * @param registers where the fleet register is kept
     * @param rosters where the day rosters are kept
     */
    public FleetFactsService(FleetRegisterRepository registers, DayRosterRepository rosters) {
        this.registers = registers;
        this.rosters = rosters;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public RegisterFacts register() {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public RosterFacts rosterOn(DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public Optional<RosteredDriver> driverOn(DeliveryDate date, AccountId driver) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public FleetChanges changesSince(DeliveryDate date, Instant since) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
