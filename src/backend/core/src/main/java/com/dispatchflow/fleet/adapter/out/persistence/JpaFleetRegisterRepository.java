package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.FleetRegister;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the company's one {@link FleetRegister} in {@code fleet.fleet_register}, under {@link
 * FleetRegister#KEY}, and its depots and vehicles in {@code fleet.depot} and {@code fleet.vehicle}.
 *
 * <p>The register loads whole: its row with its depots, then its vehicles in a second query into
 * the same persistence context, so the two lists never multiply each other's rows.
 */
@Component
@Transactional
class JpaFleetRegisterRepository implements FleetRegisterRepository {

    private final FleetRegisters registers;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param registers the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaFleetRegisterRepository(FleetRegisters registers, VersionedSave versionedSave) {
        this.registers = Objects.requireNonNull(registers, "registers");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<FleetRegister> get() {
        // The second query answers the instance the first one loaded, now with its vehicles too.
        return registers.findById(FleetRegister.KEY).flatMap(withDepots -> registers.withVehicles(FleetRegister.KEY));
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the one
     * key into {@code StaleVersion}.
     */
    @Override
    public void add(FleetRegister register) {
        versionedSave.save(register, registers::save);
    }

    @Override
    public void save(FleetRegister register) {
        versionedSave.save(register, registers::save);
    }
}
