package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.rating.RatingRequest;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaRatingRequestRepository}. */
interface RatingRequests extends Repository<RatingRequest, UUID> {

    /**
     * Saves a request and publishes its pending events.
     *
     * @param request the request
     * @return the managed request
     */
    RatingRequest save(RatingRequest request);

    /**
     * Loads a request with its comments.
     *
     * @param id the request's UUID
     * @return the request, or empty
     */
    @EntityGraph(attributePaths = "comments")
    Optional<RatingRequest> findById(UUID id);

    /**
     * The latest request whose question went to a phone and was accepted in a span, compared in the
     * phone's stored E.164 form. It returns the key alone, so that {@link #findById} loads the
     * request whole without a limit on a fetched collection.
     *
     * @param phone the phone
     * @param from the span's start, included
     * @param until the span's end, included
     * @return the request's UUID, or empty
     */
    @Query("""
            select r.id from RatingRequest r
            where r.phone = :phone and r.acceptedAt between :from and :until
            order by r.acceptedAt desc, r.id desc
            limit 1
            """)
    Optional<UUID> latestAcceptedBetween(PhoneNumber phone, Instant from, Instant until);

    /**
     * The requests whose phone, or one of whose comments, has passed its retention. Each end is the
     * stored time in Israel's calendar plus the retention period, which PostgreSQL resolves as
     * {@code MessagingRetention.endOf} does, except that a local time the autumn clock change
     * repeats is taken in standard time: an hour later, never earlier, so the request's own check
     * never finds a returned request not yet due.
     *
     * @param now when the retention job runs
     * @param zone Israel's zone ID
     * @param retention the retention period, in ISO 8601 form
     * @return the requests' UUIDs, each once
     */
    @Query(value = """
            SELECT r.id FROM messaging.rating_request r
            WHERE (r.phone IS NOT NULL
                   AND (r.accepted_at AT TIME ZONE :zone + CAST(:retention AS interval)) AT TIME ZONE :zone <= :now)
               OR EXISTS (
                   SELECT 1 FROM messaging.rating_request_comment c
                   WHERE c.rating_request_id = r.id
                     AND (c.received_at AT TIME ZONE :zone + CAST(:retention AS interval)) AT TIME ZONE :zone <= :now)
            """, nativeQuery = true)
    List<UUID> withPersonalDataDue(Instant now, String zone, String retention);
}
