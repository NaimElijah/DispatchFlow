package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.rating.RatingRequest;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps rating requests in {@code messaging.rating_request}, and their comments in {@code
 * messaging.rating_request_comment}.
 */
@Component
@Transactional
class JpaRatingRequestRepository implements RatingRequestRepository {

    private final RatingRequests ratingRequests;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param ratingRequests the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaRatingRequestRepository(
            RatingRequests ratingRequests, VersionedSave versionedSave, EntityManager entityManager) {
        this.ratingRequests = Objects.requireNonNull(ratingRequests, "ratingRequests");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<RatingRequest> get(RatingRequestId request) {
        return ratingRequests.findById(request.value());
    }

    @Override
    public void add(RatingRequest request) {
        ratingRequests.save(request);
        entityManager.flush();
    }

    @Override
    public void save(RatingRequest request) {
        versionedSave.save(request, ratingRequests::save);
    }

    @Override
    public Optional<RatingRequest> openFor(PhoneNumber phone, Instant receivedAt) {
        return ratingRequests
                .latestAcceptedBetween(phone, receivedAt.minus(RatingRequest.ANSWER_WINDOW), receivedAt)
                .flatMap(ratingRequests::findById);
    }

    @Override
    public List<RatingRequestId> withPersonalDataDue(Instant now) {
        return ratingRequests
                .withPersonalDataDue(
                        now, IsraelDateTime.ZONE.getId(), MessagingRetention.MESSAGES_AND_REPLIES.toString())
                .stream()
                .map(RatingRequestId::of)
                .toList();
    }
}
