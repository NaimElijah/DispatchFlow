package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.FleetRegister;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
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
     * Loads the register with its depots; {@link #withVehicles} then fetches its vehicles.
     *
     * @param key {@link FleetRegister#KEY}
     * @return the register, or empty before it was first stored
     */
    @EntityGraph(attributePaths = "depots")
    Optional<FleetRegister> findById(UUID key);

    /**
     * Fetches the register's vehicles, in a query of their own so they never multiply the depots'
     * rows. Run after {@link #findById} in the same transaction, it fills the vehicles of the
     * instance that call loaded and returns that instance.
     *
     * @param key {@link FleetRegister#KEY}
     * @return the register, or empty before it was first stored
     */
    @Query("select r from FleetRegister r left join fetch r.vehicles where r.id = :key")
    Optional<FleetRegister> withVehicles(UUID key);
}
