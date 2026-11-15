package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.StoredStateInvalid;
import java.util.List;

/**
 * The messages claimed today against the cap of 1,000 (NFR-196). A claimed message counts whether
 * or not the provider has answered, because it may have left ({@link
 * MessageState#countsTowardTheDailyCap}). The sender reads it before each claim; once the cap is
 * reached, sending waits for midnight, and a message still waiting 15 minutes after its trigger is
 * dropped (NFR-190). The day is Israel's, which the query that finds today's claims uses (ADR-088).
 *
 * @param claimedToday how many messages claimed today count toward the cap
 */
public record DailySendCap(int claimedToday) {

    /**
     * The most messages claimed in a day (NFR-196). It counts claims, not acceptances, because a
     * claimed message may have left (ADR-108).
     */
    public static final int LIMIT = 1_000;

    /**
     * Checks the count.
     *
     * @throws StoredStateInvalid when the count is negative, which no stored messages give
     */
    public DailySendCap {
        if (claimedToday < 0) {
            throw new StoredStateInvalid(DailySendCap.class, "a count of claimed messages is never negative");
        }
    }

    /**
     * Counts the messages claimed today that count toward the cap.
     *
     * @param claimedToday the states of the messages claimed today
     * @return the cap
     */
    public static DailySendCap counting(List<MessageState> claimedToday) {
        return new DailySendCap((int) List.copyOf(claimedToday).stream()
                .filter(MessageState::countsTowardTheDailyCap)
                .count());
    }

    /**
     * Whether another message may be sent today.
     *
     * @return {@code true} while fewer than 1,000 count
     */
    public boolean hasRoom() {
        return claimedToday < LIMIT;
    }
}
