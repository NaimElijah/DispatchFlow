package com.dispatchflow.reporting.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind the settlements {@link JpaBaselineRepository} keeps. Each
 * statement settles a plan unless it is settled already, and answers which, so a second settlement
 * is a conflict the caller sees without a failed statement aborting its transaction.
 */
interface BaselineOutcomes extends Repository<StoredBaselineOutcome, UUID> {

    /**
     * Settles a plan as having a computed baseline, unless it is settled already.
     *
     * @param plan the plan's UUID
     * @return 1 when it was settled now, 0 when it was settled before
     */
    @Modifying
    @Query(value = """
            INSERT INTO reporting.baseline_outcome (plan_id, outcome)
            VALUES (:plan, 'COMPUTED')
            ON CONFLICT (plan_id) DO NOTHING
            """, nativeQuery = true)
    int settleAsComputed(UUID plan);

    /**
     * Settles a plan as refused by the optimizer, unless it is settled already.
     *
     * @param plan the plan's UUID
     * @param cause the refusal cause's name
     * @return 1 when it was settled now, 0 when it was settled before
     */
    @Modifying
    @Query(value = """
            INSERT INTO reporting.baseline_outcome (plan_id, outcome, refusal_cause)
            VALUES (:plan, 'REFUSED', :cause)
            ON CONFLICT (plan_id) DO NOTHING
            """, nativeQuery = true)
    int settleAsRefused(UUID plan, String cause);

    /**
     * Loads how a plan was settled.
     *
     * @param plan the plan's UUID
     * @return the settlement, or empty while the plan is not settled
     */
    Optional<StoredBaselineOutcome> findById(UUID plan);
}
