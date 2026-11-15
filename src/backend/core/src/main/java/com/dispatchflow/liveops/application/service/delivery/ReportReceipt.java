package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.liveops.application.port.out.RecordedReport;
import java.util.Objects;

/**
 * What a driver's report did, as the phone's outbox is answered (FR-248, ADR-087): the fact was
 * recorded, or the report is held for a dispatcher, and whether this answer is the stored one of an
 * earlier send with the same key (BR-28). Either way the outbox deletes its row.
 *
 * <p>Built in M5.
 *
 * @param answer what the report did
 * @param replayed whether the answer was stored by an earlier send of the same request, so nothing
 *     changed this time (NFR-65)
 */
public record ReportReceipt(RecordedReport answer, boolean replayed) {

    /** Checks the answer is present. */
    public ReportReceipt {
        Objects.requireNonNull(answer, "answer");
    }
}
