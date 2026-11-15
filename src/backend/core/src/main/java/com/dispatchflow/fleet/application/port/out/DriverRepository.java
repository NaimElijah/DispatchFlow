package com.dispatchflow.fleet.application.port.out;

import com.dispatchflow.fleet.domain.driver.Driver;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Where the driver records are kept, each keyed by its account's ID (DL §3.6, §4.2), and published
 * with their {@code DriverRetired} events.
 *
 * <p>Built in M2.
 */
public interface DriverRepository {

    /**
     * The driver record of an account.
     *
     * <p>Built in M2.
     *
     * @param id the driver's account
     * @return the record, or empty when the account has none
     */
    Optional<Driver> get(AccountId id);

    /**
     * Every driver record, in any state, whose defaults a roster fill reads (FR-79, FR-189); {@code
     * Driver.rosterDefaultOn} answers empty for one retired or anonymized.
     *
     * <p>Built in M2.
     *
     * @return the records, in the order they were registered
     */
    List<Driver> registered();

    /**
     * The retired drivers whose retention is over: retired at least a year before today, in
     * Israel's calendar, and not yet anonymized (BR-98, NFR-184, ADR-075). It answers IDs, so each
     * record is loaded, changed and saved in its own transaction.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param today today's date in {@code Asia/Jerusalem}
     * @return their IDs, in any order
     */
    List<AccountId> dueForAnonymization(LocalDate today);

    /**
     * Stores a new driver record (DL §3.6).
     *
     * <p>Built in M2.
     *
     * @param driver the new record, at {@code Version.NEW}
     */
    void add(Driver driver);

    /**
     * Stores a changed driver record with a version check, and publishes the events it raised in
     * the same transaction (DL §3.6, BR-76).
     *
     * <p>Built in M2.
     *
     * @param driver the changed record
     * @throws StaleVersion when the stored record is no longer at the version it was loaded at
     */
    void save(Driver driver);
}
