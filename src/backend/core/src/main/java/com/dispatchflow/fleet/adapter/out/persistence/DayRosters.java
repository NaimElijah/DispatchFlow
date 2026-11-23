package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.roster.DayRoster;
import com.dispatchflow.shared.DeliveryDate;
import java.util.Optional;
import java.util.UUID;
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
     * Loads a date's roster row; the adapter loads its entries and unavailable vehicles.
     *
     * @param date the delivery date, which the table keeps unique
     * @return the roster, or empty
     */
    Optional<DayRoster> findByDate(DeliveryDate date);
}
