package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.application.port.out.FleetRegisterRepository;
import com.dispatchflow.fleet.domain.register.FleetRegister;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the company's one {@link FleetRegister} in {@code fleet.fleet_register}, under {@link
 * FleetRegister#KEY}, and its depots and vehicles in {@code fleet.depot} and {@code fleet.vehicle}.
 *
 * <p>The register loads whole: its row, then its depots and its vehicles, each list in a query of
 * its own, so the two lists never multiply each other's rows.
 */
@Repository
@Transactional
class JpaFleetRegisterRepository implements FleetRegisterRepository {

    private final FleetRegisters registers;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param registers the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which loads the register's lists
     */
    JpaFleetRegisterRepository(FleetRegisters registers, VersionedSave versionedSave, EntityManager entityManager) {
        this.registers = Objects.requireNonNull(registers, "registers");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<FleetRegister> get() {
        Optional<FleetRegister> register = registers.findById(FleetRegister.KEY);
        PersistenceUnitUtil units = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        register.ifPresent(found -> {
            units.load(found, "depots");
            units.load(found, "vehicles");
        });
        return register;
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
