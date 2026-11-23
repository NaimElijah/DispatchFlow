package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.driver.Driver;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaDriverRepository}. */
interface Drivers extends Repository<Driver, UUID> {

    /**
     * Saves a driver and publishes their pending events.
     *
     * @param driver the driver
     * @return the managed driver
     */
    Driver save(Driver driver);

    /**
     * Loads a driver with their weekly shifts.
     *
     * @param account the account's UUID
     * @return the driver, or empty
     */
    @EntityGraph(attributePaths = "weeklyShifts")
    Optional<Driver> findById(UUID account);

    /**
     * Loads drivers with their weekly shifts.
     *
     * @param accounts the accounts' UUIDs
     * @return the drivers among them, in no order
     */
    @EntityGraph(attributePaths = "weeklyShifts")
    List<Driver> findByIdIn(Collection<UUID> accounts);

    /**
     * Every driver's key, in the order they were registered: the number the database drew when it
     * inserted each row, which the entity doesn't map.
     *
     * @return the accounts' UUIDs
     */
    @Query(value = "SELECT id FROM fleet.driver ORDER BY registration_order", nativeQuery = true)
    List<UUID> inRegistrationOrder();

    /**
     * The retired drivers whose retention has ended. The end is the retirement in Israel's calendar
     * plus the retention period, which PostgreSQL resolves as {@code Driver.anonymize} does, except
     * that a local time the autumn clock change repeats is taken in standard time: an hour later,
     * never earlier, so the driver's own check never refuses a driver this returns.
     *
     * @param now when the retention job runs
     * @param zone Israel's zone ID
     * @param retention the retention period, in ISO 8601 form
     * @return the accounts' UUIDs, in no order
     */
    @Query(value = """
            SELECT id FROM fleet.driver
            WHERE status = 'RETIRED'
              AND (retired_at AT TIME ZONE :zone + CAST(:retention AS interval)) AT TIME ZONE :zone <= :now
            """, nativeQuery = true)
    List<UUID> retentionEndedBy(Instant now, String zone, String retention);
}
