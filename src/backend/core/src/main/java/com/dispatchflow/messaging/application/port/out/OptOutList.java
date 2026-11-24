package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.OptOutRecord;
import java.util.List;

/**
 * The phones' opt-out history: entries appended and never changed, kept as a keyed hash of the phone
 * until the company leaves, whatever retention or a request removes (DL §3.2, FR-363, FR-380,
 * NFR-202, ADR-108).
 */
public interface OptOutList {

    /**
     * A phone's history under every key version still in use, which {@code OptOutStatus} judges, so
     * an opt-out made before a key rotation still counts (ADR-069, NFR-202).
     *
     * <p>{@code OptOutStatus.of} takes one {@code PhoneHash} and refuses an entry under another, so
     * it can't judge a history read under two keys yet. Reading one under several key versions,
     * with the version {@code PhoneHash} gains then, is not built yet (a domain gap).
     *
     * @param phone the phone's keyed hashes, the current one and the older ones still in use
     * @return its entries under all of them, in the order they were appended; empty when it has none
     */
    List<OptOutRecord> historyOf(PhoneHashes phone);

    /**
     * Appends an entry, in the caller's transaction.
     *
     * @param entry the entry
     */
    void append(OptOutRecord entry);
}
