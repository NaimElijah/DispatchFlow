package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.register.FleetRegister;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where the company's one fleet register is kept, whole, with every depot and vehicle (DL §3.6,
 * §4.2). It has no ID, because there is one.
 *
 * <p>Built in M2.
 */
public interface FleetRegisterRepository {

    /**
     * The fleet register.
     *
     * <p>Built in M2.
     *
     * @return the register, or empty before it is first stored
     */
    Optional<FleetRegister> get();

    /**
     * Stores the register the first time, from {@code FleetRegister.empty()} (DL §3.6).
     *
     * <p>Built in M2.
     *
     * @param register the new register, never saved
     * @throws StaleVersion when another call stored the register first
     */
    void add(FleetRegister register);

    /**
     * Stores the changed register with a version check (DL §3.6, BR-76). The one version replaces a
     * set-wide check and a lock (DL §16).
     *
     * <p>Built in M2.
     *
     * @param register the changed register
     * @throws StaleVersion when the stored register is no longer at the version it was loaded at
     */
    void save(FleetRegister register);
}
