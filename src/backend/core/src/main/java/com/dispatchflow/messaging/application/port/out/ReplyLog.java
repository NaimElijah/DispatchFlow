package com.dispatchflow.messaging.application.port.out;

import java.time.Instant;

/**
 * The replies kept: records appended and never changed, each unique by the provider's identifier,
 * so a provider call received twice takes effect once (DL §3.2, NFR-199). A reply with no rating
 * request in the last 48 hours is not kept, except an opt-out (FR-379). Replies are deleted 90 days
 * after they were received (NFR-184).
 */
public interface ReplyLog {

    /**
     * Appends a reply unless one with its provider identifier is kept, in the caller's transaction.
     * It writes what the transaction holds unwritten before it appends, so called between a change
     * and its save it would write the change there: the save may then fail, or what this reads may
     * include the unsaved change (ADR-128).
     *
     * @param reply the reply
     * @return {@code true} when it was appended, {@code false} when the provider sent it before
     */
    boolean append(InboundReply reply);

    /**
     * Deletes every reply received before a cutoff (NFR-184, ADR-075).
     *
     * @param cutoff the first receipt time whose replies are kept: the retention job's time less 90
     *     Israel calendar days, at the same Israel time (ADR-088)
     * @return how many replies were deleted
     */
    int deleteOlderThan(Instant cutoff);
}
