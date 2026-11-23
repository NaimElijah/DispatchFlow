package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.FleetRegister;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaFleetRegisterRepository}. */
interface FleetRegisters extends Repository<FleetRegister, UUID> {

    /**
     * Saves the register.
     *
     * @param register the register
     * @return the managed register
     */
    FleetRegister save(FleetRegister register);

    /**
     * Loads the register's row; the adapter loads its depots and vehicles.
     *
     * @param key {@link FleetRegister#KEY}
     * @return the register, or empty before it was first stored
     */
    Optional<FleetRegister> findById(UUID key);
}
