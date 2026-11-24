package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.AccountState;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * One account as the users screen shows it (the permission matrix, "Read the company's details and
 * its users"; FR-02, FR-53, FR-294). It holds the account's address, so it prints redacted
 * (NFR-19).
 *
 * <p>To Be Implemented.
 *
 * @param account the account
 * @param email the address it signs in with; empty once anonymized (NFR-260)
 * @param roles its roles, perhaps none (BR-123)
 * @param state active, deactivated or anonymized, with when (FR-53, FR-297)
 * @param language its interface language (FR-124)
 * @param passwordSet whether its password is set, so an Admin sees who still needs a set-password
 *     link (FR-294)
 * @param version the account's version, which an Admin's change of the account sends back as
 *     {@code If-Match} (BR-76)
 */
public record AccountView(
        AccountId account,
        Optional<EmailAddress> email,
        RoleSet roles,
        AccountState state,
        Language language,
        boolean passwordSet,
        Version version)
        implements PersonalData {

    /** Checks every part is present, possibly empty. */
    public AccountView {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that hides the address.
     *
     * @return {@code AccountView[redacted]}
     */
    @Override
    public String toString() {
        return "AccountView[redacted]";
    }
}
