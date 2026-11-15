package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.rating.RatingRequest;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where rating requests are kept (DL §3.6, §4.6). Saving a request publishes its {@code
 * RatingReceived}, in the caller's transaction (ADR-064).
 *
 * <p>Built in M12.
 */
public interface RatingRequestRepository {

    /**
     * One rating request.
     *
     * <p>Built in M12.
     *
     * @param request the request's ID
     * @return the request, or empty when no request has the ID
     */
    Optional<RatingRequest> get(RatingRequestId request);

    /**
     * Stores a new request, opened when the provider accepted its question, in the transaction of
     * that acceptance (DL §6.2).
     *
     * <p>Built in M12.
     *
     * @param request the request, at {@code Version.NEW}
     */
    void add(RatingRequest request);

    /**
     * Stores a changed request, checking the version it was loaded at (BR-76).
     *
     * <p>Built in M12.
     *
     * @param request the request
     * @throws StaleVersion when another reply saved it since it was loaded
     */
    void save(RatingRequest request);

    /**
     * The request a reply from a phone answers: the latest one sent to it, if its question was
     * accepted within the 48 hours before the reply (FR-357, FR-371, FR-379). Phones are compared in
     * their stored E.164 form (FR-377).
     *
     * <p>Built in M12.
     *
     * @param phone the reply's phone
     * @param receivedAt when the reply was received
     * @return the request, or empty when the phone has none in the 48 hours, so the reply is
     *     discarded unless it opts out
     */
    Optional<RatingRequest> openFor(PhoneNumber phone, Instant receivedAt);

    /**
     * The requests holding personal data whose retention has ended: a phone 90 days after its
     * question was accepted, or a comment 90 days after its reply (NFR-184, NFR-193).
     *
     * <p>Built in M12.
     *
     * @param now when the retention job runs
     * @return the requests, each once, for the job to clear one at a time
     */
    List<RatingRequestId> withPersonalDataDue(Instant now);
}
