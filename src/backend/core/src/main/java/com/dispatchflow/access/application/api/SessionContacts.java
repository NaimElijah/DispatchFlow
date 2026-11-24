package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.List;

/**
 * When any session of an account was in contact with the server, which Live Operations' {@code
 * HoldCheck} judges a late report by (BR-132, BR-133, ADR-107, placement P8). A token refresh and
 * an outbox send are not contact (DL §8).
 *
 * <p>To Be Implemented.
 */
public interface SessionContacts {

    /**
     * The contact times of every session of an account within a span, both ends included.
     *
     * <p>To Be Implemented.
     *
     * @param account the reporting account
     * @param from the first instant of the span
     * @param to the last instant of the span, not before {@code from}
     * @return the contact times in the span, in time order; empty when there was none
     */
    List<Instant> contactsOf(AccountId account, Instant from, Instant to);
}
