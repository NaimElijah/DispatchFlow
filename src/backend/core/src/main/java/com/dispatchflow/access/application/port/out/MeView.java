package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * The signed-in user's own view: who they are, the roles that decide what they may do, their
 * language, whether they accepted the terms, and whether an Admin is warned that fewer than two
 * Admins are active (FR-03, FR-77, FR-181, FR-299). It holds the user's address, so it prints
 * redacted (NFR-19).
 *
 * <p>Built in M5.
 *
 * @param account the account
 * @param email the address the user signs in with
 * @param roles the roles the account holds (FR-03, FR-293)
 * @param language the interface language, which follows the user to every device (FR-181)
 * @param adminsBelowMinimum whether to warn the user, an Admin, that the company has fewer than two
 *     active Admins; {@code false} for any other user (FR-299, BR-19)
 * @param termsAccepted whether the user has accepted the terms and the privacy notice, so the apps
 *     ask for it only while it is {@code false}, and never during a route (FR-77)
 * @param version the account's version, which a change of the account sends back as {@code
 *     If-Match} (BR-76)
 */
public record MeView(
        AccountId account,
        EmailAddress email,
        RoleSet roles,
        Language language,
        boolean adminsBelowMinimum,
        boolean termsAccepted,
        Version version)
        implements PersonalData {

    /** Checks every part is present. */
    public MeView {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(version, "version");
    }

    /**
     * A form that hides the address.
     *
     * @return {@code MeView[redacted]}
     */
    @Override
    public String toString() {
        return "MeView[redacted]";
    }
}
