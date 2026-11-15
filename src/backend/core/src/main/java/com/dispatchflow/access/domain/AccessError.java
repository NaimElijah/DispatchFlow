package com.dispatchflow.access.domain;

import com.dispatchflow.shared.DomainError;
import java.time.LocalTime;

/**
 * The Access context's refusals (DL §4.1). Their fields become a response's parameters, so none
 * carries an email address, a password, a hash or a token (NFR-94, ADR-043).
 */
public sealed interface AccessError extends DomainError {

    /** Text that is not an email address one account can be keyed by (BR-45). It names no address. */
    record InvalidEmail() implements AccessError {}

    /** Another account already holds the address (BR-45, FR-392). It names neither. */
    record EmailInUse() implements AccessError {}

    /**
     * Another account asked to change an Admin's email, which only that Admin may do (FR-387,
     * ADR-116). It is refused when the link would be sent, and when it is used if the account became
     * an Admin in between.
     */
    record EmailOfAnotherAdmin() implements AccessError {}

    /** An Admin gave a user no role (FR-02). */
    record NoRoleGiven() implements AccessError {}

    /**
     * The change would take the Admin role from one of the last two active Admins (BR-19, FR-174).
     *
     * @param activeAdmins how many active Admins the company has
     */
    record TooFewAdmins(int activeAdmins) implements AccessError {}

    /** The first Admin is created only while no active Admin exists (FR-291). */
    record ActiveAdminExists() implements AccessError {}

    /** The written-request step deactivates only Admin accounts (FR-346). */
    record NotAnAdmin() implements AccessError {}

    /**
     * A new password shorter than the minimum (NFR-95).
     *
     * @param minimumLength the fewest characters a password may have
     */
    record PasswordTooShort(int minimumLength) implements AccessError {}

    /** A new password on the list of the most common ones (NFR-95). */
    record PasswordTooCommon() implements AccessError {}

    /**
     * A new password holding half of a surrogate pair without the other half, which no encoding
     * represents reliably, so the hash of what was typed could differ from the hash of what is
     * checked later (NFR-95).
     */
    record PasswordHasUnpairedSurrogate() implements AccessError {}

    /**
     * The account has no password yet: its set-password link was never used, so it can't sign in
     * (FR-294). The sign-in endpoint answers it as it answers a wrong password (NFR-145).
     */
    record PasswordNotSet() implements AccessError {}

    /**
     * The account is deactivated, so it can't sign in, and only reactivating it, anonymizing it or
     * removing its Driver role changes it (FR-53, FR-297, FR-307).
     */
    record AccountDeactivated() implements AccessError {}

    /** The account holds no role, so it can't sign in and recovery sends it nothing (BR-123). */
    record AccountHasNoRole() implements AccessError {}

    /** Only a deactivated account is reactivated or anonymized (FR-297, NFR-258). */
    record AccountNotDeactivated() implements AccessError {}

    /** The account was anonymized, so it is a record only and nothing changes it (NFR-260). */
    record AccountAnonymized() implements AccessError {}

    /** An account is anonymized only a year after its deactivation (NFR-258, ADR-075). */
    record RetentionNotOver() implements AccessError {}

    /**
     * A set-password link is only for an account with no password yet; a reset goes through the
     * 1-hour recovery link (FR-294, FR-345; ADR-104).
     */
    record PasswordAlreadySet() implements AccessError {}

    /** The account holds no link of the kind needed with the presented token (FR-294). */
    record UnknownLink() implements AccessError {}

    /** The link's time ran out before it was used (FR-294, FR-345, FR-388). */
    record LinkExpired() implements AccessError {}

    /** The link was already used, and works once (NFR-211, NFR-224). */
    record LinkAlreadyUsed() implements AccessError {}

    /** A newer link, or a change of email or password, voided the link (NFR-224). */
    record LinkVoided() implements AccessError {}

    /** A refresh token is refused; every refusal looks the same (NFR-92). */
    record RefreshRefused() implements AccessError {}

    /** An Admin-only operation needs the password given in the last 5 minutes (NFR-214). */
    record StepUpRequired() implements AccessError {}

    /** The session was revoked, so nothing changes it any more (NFR-89). */
    record SessionRevoked() implements AccessError {}

    /** A company name that is empty once trimmed (FR-125). */
    record BlankCompanyName() implements AccessError {}

    /**
     * A company name too long for every message to fit one SMS (FR-382).
     *
     * @param maximumLength the most characters a company name may have
     */
    record CompanyNameTooLong(int maximumLength) implements AccessError {}

    /**
     * A new customer's default window whose opening is not before its closing (FR-125, BR-35).
     *
     * @param opens the refused opening time
     * @param closes the refused closing time
     */
    record InvalidDefaultWindow(LocalTime opens, LocalTime closes) implements AccessError {}

    /**
     * A new customer's default service duration that is zero or negative (FR-184).
     *
     * @param seconds the refused duration, in seconds
     */
    record NonPositiveDefaultServiceTime(long seconds) implements AccessError {}
}
