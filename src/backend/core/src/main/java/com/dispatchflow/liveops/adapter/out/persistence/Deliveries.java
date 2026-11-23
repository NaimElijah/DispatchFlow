package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.delivery.Delivery;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * The Spring Data interface behind {@link JpaDeliveryRepository}: {@code save}, and the lookups the
 * port needs. The lookups that find a delivery answer its key alone, so that {@link #findById} and
 * {@link #withHeldReports} load it whole. The fetch of the held reports runs although the
 * delivery's load check reads them while the delivery loads: loading whole must not depend on
 * what the check reads, because Jakarta Persistence calls touching a relationship in a lifecycle
 * callback unportable.
 */
interface Deliveries extends Repository<Delivery, UUID> {

    /**
     * Saves a delivery and publishes its pending events.
     *
     * @param delivery the delivery
     * @return the managed delivery
     */
    Delivery save(Delivery delivery);

    /**
     * Loads a delivery with its outcome records; {@link #withHeldReports} then fetches its held
     * reports, so the two collections don't multiply each other's rows.
     *
     * @param stop the stop's UUID
     * @return the delivery, or empty
     */
    @EntityGraph(attributePaths = "outcomes")
    Optional<Delivery> findById(UUID stop);

    /**
     * Fetches a delivery's held reports into the instance this transaction already holds, whether
     * or not its load check read them.
     *
     * @param stop the stop's UUID
     * @return the delivery, or empty
     */
    @Query("select d from Delivery d left join fetch d.heldReports where d.id = :stop")
    Optional<Delivery> withHeldReports(UUID stop);

    /**
     * Loads several deliveries with their outcome records; {@link #withHeldReportsIn} then fetches
     * their held reports.
     *
     * @param stops the stops' UUIDs
     * @return the deliveries found, each once, in no order
     */
    @EntityGraph(attributePaths = "outcomes")
    List<Delivery> findByIdIn(Collection<UUID> stops);

    /**
     * Fetches the held reports of several deliveries into the instances this transaction already
     * holds, whether or not their load checks read them.
     *
     * @param stops the stops' UUIDs
     * @return the deliveries found, each once, in no order
     */
    @Query("select d from Delivery d left join fetch d.heldReports where d.id in :stops")
    List<Delivery> withHeldReportsIn(Collection<UUID> stops);

    /**
     * The stop of one attempt of an order.
     *
     * @param order the order
     * @param attempt the attempt
     * @return the stop's UUID, or empty when no delivery is recorded for it
     */
    @Query("select d.id from Delivery d where d.order = :order and d.attempt = :attempt")
    Optional<UUID> stopOf(OrderId order, Attempt attempt);

    /**
     * The stops whose delivery still holds personal data recorded at or before its cutoff: a
     * proof's file, or the text of an <em>other</em> reason, or of a correction's reason. An
     * outcome's personal data counts from when it reached the server, as {@code
     * Delivery.eraseExpired} reads it: its report's receipt, its held report's receipt once
     * confirmed, the record's own time on a driver's behalf, and the correction's time.
     *
     * @param proofCutoff the latest receipt time whose proofs are due
     * @param reasonCutoff the latest time whose reason texts are due
     * @return the stops' UUIDs, each once, in no order
     */
    @Query(value = """
            SELECT o.delivery_id FROM liveops.delivery_outcome o
            LEFT JOIN liveops.held_report h ON h.delivery_id = o.delivery_id AND h.id = o.confirmed_report
            CROSS JOIN LATERAL (SELECT CASE o.origin
                    WHEN 'REPORTED' THEN o.report_received_at
                    WHEN 'CONFIRMED_HELD' THEN h.received_at
                    WHEN 'ON_BEHALF' THEN o.fact_time
                    ELSE o.corrected_at END AS recorded_at) r
            WHERE (o.proof_file IS NOT NULL AND r.recorded_at <= :proofCutoff)
               OR (o.failure_text IS NOT NULL AND r.recorded_at <= :reasonCutoff)
               OR (o.correction_reason IS NOT NULL AND o.corrected_at <= :reasonCutoff)
            UNION
            SELECT h.delivery_id FROM liveops.held_report h
            WHERE (h.proof_file IS NOT NULL AND h.received_at <= :proofCutoff)
               OR (h.failure_text IS NOT NULL AND h.received_at <= :reasonCutoff)
            """, nativeQuery = true)
    List<UUID> withPersonalDataRecordedBy(Instant proofCutoff, Instant reasonCutoff);
}
