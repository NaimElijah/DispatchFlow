package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.shared.DeliveryDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaDayRosterRepository}. */
interface DayRosters extends Repository<DayRoster, UUID> {

    /**
     * Saves a roster.
     *
     * @param roster the roster
     * @return the managed roster
     */
    DayRoster save(DayRoster roster);

    /**
     * Loads a date's roster with its entries; {@link #withUnavailableVehicles} then fetches its
     * unavailable vehicles.
     *
     * @param date the delivery date, which the table keeps unique
     * @return the roster, or empty
     */
    @EntityGraph(attributePaths = "entries")
    Optional<DayRoster> findByDate(DeliveryDate date);

    /**
     * Fetches a date's unavailable vehicles, in a query of their own so they never multiply the
     * entries' rows. Run after {@link #findByDate} in the same transaction, it fills the vehicles of
     * the instance that call loaded and returns that instance.
     *
     * @param date the delivery date
     * @return the roster, or empty
     */
    @Query("select r from DayRoster r left join fetch r.unavailableVehicles where r.date = :date")
    Optional<DayRoster> withUnavailableVehicles(DeliveryDate date);
}
