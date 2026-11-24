package com.dispatchflow.fleet.application.service;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.fleet.application.api.AssignabilityFact;
import com.dispatchflow.fleet.application.api.FleetChanges;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.fleet.application.api.RegisterFacts;
import com.dispatchflow.fleet.application.api.RosterFacts;
import com.dispatchflow.fleet.application.api.RosteredDriver;
import com.dispatchflow.fleet.application.port.out.DayRosterRepository;
import com.dispatchflow.fleet.application.port.out.DriverRepository;
import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fleet's implementation of {@code FleetFacts}: the register and the rosters as Planning and Live
 * Operations read them, translated from Fleet's own values (DL §2.2, §2.3 rule 1, ADR-056).
 *
 * <p>Each driver's standing combines Access's {@link Accounts} answer, asked at each read, with the
 * driver record's {@code DriverStatus}, read through {@code Driver.status} (ADR-062, ADR-103):
 * {@link #rosterOn} asks {@code standingsOf} once for the date's drivers and keeps the active ones
 * (DL §4.2); {@link #driverOn} and {@link #assignability} ask {@code standingOf} for one. The
 * combined standing becomes the domain's {@code Standing} for {@code DayRoster.checkAssignable},
 * and its refusal becomes an {@link AssignabilityFact.Reason}.
 *
 * <p>Neither {@code FleetRegister} nor {@code DayRoster} records when an entry changed, which
 * {@link #changesSince} needs; that record is not added yet.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class FleetFactsService implements FleetFacts {

    private final FleetRegisterRepository registers;
    private final DayRosterRepository rosters;
    private final DriverRepository drivers;
    private final Accounts accounts;

    /**
     * Creates the service.
     *
     * @param registers where the fleet register is kept
     * @param rosters where the day rosters are kept
     * @param drivers where the driver records are kept, for each record's state
     * @param accounts Access's answer on whether an account is active and holds the Driver role
     */
    public FleetFactsService(
            FleetRegisterRepository registers,
            DayRosterRepository rosters,
            DriverRepository drivers,
            Accounts accounts) {
        this.registers = registers;
        this.rosters = rosters;
        this.drivers = drivers;
        this.accounts = accounts;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public RegisterFacts register() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public RosterFacts rosterOn(DeliveryDate date) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public Optional<RosteredDriver> driverOn(DeliveryDate date, AccountId driver) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public AssignabilityFact assignability(DeliveryDate date, VehicleId vehicle, AccountId driver) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public FleetChanges changesSince(DeliveryDate date, Instant since) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
