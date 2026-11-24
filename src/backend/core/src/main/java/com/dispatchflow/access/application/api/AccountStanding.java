package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.AccountId;
import java.util.Objects;

/**
 * Whether an account may drive, as Access answers it now (DL §2.2): Fleet translates it into its
 * driver {@code Standing}, and Planning into {@code DriverStanding.activeDriver} (FR-06, FR-302,
 * BR-20).
 *
 * <p>To Be Implemented.
 *
 * @param account the account
 * @param activeDriver whether the account is active and holds the Driver role
 */
public record AccountStanding(AccountId account, boolean activeDriver) {

    /** Checks the account is present. */
    public AccountStanding {
        Objects.requireNonNull(account, "account");
    }
}
