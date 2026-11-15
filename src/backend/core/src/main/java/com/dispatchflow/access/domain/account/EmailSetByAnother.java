package com.dispatchflow.access.domain.account;

import com.dispatchflow.shared.AccountId;
import java.time.Instant;
import java.util.Objects;

/**
 * Which account's email-change link set an account's current address, and when (ADR-116). The
 * account that set it can't give that account the Admin role; another Admin can, which is the
 * second person ADR-116 requires.
 *
 * @param by the account that asked for the change, never the account whose address it set
 * @param at when the address took effect
 */
public record EmailSetByAnother(AccountId by, Instant at) {

    /** Checks both parts are present. */
    public EmailSetByAnother {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(at, "at");
    }
}
