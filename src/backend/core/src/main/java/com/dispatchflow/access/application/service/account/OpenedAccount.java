package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;
import java.util.Optional;

/**
 * What the transaction that opens an account leaves its use case to send after the commit (ADR-129):
 * the new account's set-password link (FR-294), and the notice every active Admin gets when the
 * account opens with the Admin role (FR-389). It holds addresses and a token, so it prints redacted
 * (NFR-19).
 *
 * <p>Built in M6.
 *
 * @param account the new account's ID
 * @param link the set-password link's email
 * @param adminNotice the Admins' notice, or empty when none is due
 */
public record OpenedAccount(AccountId account, LinkEmail link, Optional<AdminNotice> adminNotice)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public OpenedAccount {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(adminNotice, "adminNotice");
    }

    /**
     * A form that hides the addresses and the token.
     *
     * @return {@code OpenedAccount[redacted]}
     */
    @Override
    public String toString() {
        return "OpenedAccount[redacted]";
    }
}
