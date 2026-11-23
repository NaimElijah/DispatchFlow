package com.dispatchflow.access.domain.account;

import com.dispatchflow.shared.AccountId;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * Which account's email-change link set an account's current address, and when (ADR-116). The
 * account that set it can't give that account the Admin role; another Admin can, which is the
 * second person ADR-116 requires.
 *
 * <p>It is stored in its account's columns (ADR-126). Its constructor only checks that both parts
 * are present, so Hibernate builds it through its canonical constructor, and the migration's {@code
 * CHECK} keeps its two columns both present or both absent.
 *
 * @param by the account that asked for the change, never the account whose address it set
 * @param at when the address took effect
 */
@Embeddable
public record EmailSetByAnother(AccountId by, Instant at) {

    /** Checks both parts are present. */
    public EmailSetByAnother {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(at, "at");
    }
}
