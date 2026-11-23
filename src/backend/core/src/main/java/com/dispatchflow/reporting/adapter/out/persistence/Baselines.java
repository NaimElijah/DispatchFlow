package com.dispatchflow.reporting.adapter.out.persistence;

import com.dispatchflow.reporting.domain.baseline.Baseline;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaBaselineRepository}. A baseline has three collections,
 * so {@link #findById} fetches one and a query each fetches the other two into the same persistence
 * context, which keeps each query's rows to one collection's (ADR-126).
 */
interface Baselines extends Repository<Baseline, UUID> {

    /**
     * Saves a baseline.
     *
     * @param baseline the baseline
     * @return the managed baseline
     */
    Baseline save(Baseline baseline);

    /**
     * Loads a baseline with its planned orders.
     *
     * @param id the plan's UUID
     * @return the baseline, or empty
     */
    @EntityGraph(attributePaths = "plannedOrders")
    Optional<Baseline> findById(UUID id);

    /**
     * Fetches a baseline's withdrawn vehicles into the persistence context.
     *
     * @param id the plan's UUID
     * @return the baseline, or empty
     */
    @Query("select b from Baseline b left join fetch b.withdrawn where b.id = :id")
    Optional<Baseline> fetchWithdrawn(UUID id);

    /**
     * Fetches a baseline's added orders into the persistence context.
     *
     * @param id the plan's UUID
     * @return the baseline, or empty
     */
    @Query("select b from Baseline b left join fetch b.added where b.id = :id")
    Optional<Baseline> fetchAdded(UUID id);
}
