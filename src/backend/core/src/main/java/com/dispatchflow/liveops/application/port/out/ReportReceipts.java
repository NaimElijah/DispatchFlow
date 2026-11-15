package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;

/**
 * The phone's idempotency record, keyed by the user and the request key: each idempotent phone
 * write is Live Operations' own, so Live Operations owns it (ADR-087, NFR-65, NFR-130, NFR-285,
 * placement P2). The listeners' {@code (listener, event ID)} pairs are the shared {@code
 * ProcessedEvents}, not this.
 *
 * <p>Built in M8.
 */
public interface ReportReceipts {

    /**
     * A request already taken under a key.
     *
     * <p>Built in M8.
     *
     * @param user the signed-in user who sent it
     * @param key the request key
     * @return the stored body hash and answer, or empty when the key is new for the user
     */
    Optional<Receipt> find(AccountId user, IdempotencyKey key);

    /**
     * Records a request's answer under its key, in the transaction of the change and its audit
     * record (ADR-087).
     *
     * <p>Built in M8.
     *
     * @param user the signed-in user who sent it
     * @param key the request key
     * @param digest the request body's hash
     * @param answer what the request did
     * @throws StaleVersion when a concurrent send recorded the key first; the retry then gets the
     *     stored answer
     */
    void record(AccountId user, IdempotencyKey key, RequestDigest digest, RecordedReport answer);

    /**
     * A stored request: its body's hash and its answer.
     *
     * <p>Built in M8.
     *
     * @param digest the body's hash, which a retry must match (NFR-130)
     * @param answer what the request did
     */
    record Receipt(RequestDigest digest, RecordedReport answer) {

        /** Checks both parts are present. */
        public Receipt {
            Objects.requireNonNull(digest, "digest");
            Objects.requireNonNull(answer, "answer");
        }
    }
}
