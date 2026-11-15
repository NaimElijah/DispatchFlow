package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.OptOutRecord;
import com.dispatchflow.messaging.domain.PhoneHash;
import java.util.List;

/**
 * The phones' opt-out history: entries appended and never changed, kept as a keyed hash of the phone
 * until the company leaves, whatever retention or a request removes (DL §3.2, FR-363, FR-380,
 * NFR-202, ADR-108).
 *
 * <p>Built in M12.
 */
public interface OptOutList {

    /**
     * A phone's history, which {@code OptOutStatus} judges.
     *
     * <p>Built in M12.
     *
     * @param phone the phone's keyed hash
     * @return its entries in the order they were appended; empty when it has none
     */
    List<OptOutRecord> historyOf(PhoneHash phone);

    /**
     * Appends an entry, in the caller's transaction.
     *
     * <p>Built in M12.
     *
     * @param entry the entry
     */
    void append(OptOutRecord entry);
}
