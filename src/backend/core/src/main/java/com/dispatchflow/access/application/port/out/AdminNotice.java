package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * The email every active Admin gets when an account gains the Admin role, an Admin account is
 * reactivated, or an Admin's password changes or new email takes effect (FR-389). It names the
 * account by its ID only. It holds the Admins' addresses, so it prints redacted (NFR-19).
 *
 * <p>Built in M6.
 *
 * @param reason what happened
 * @param account the account it happened to
 * @param recipients every active Admin, each once
 */
public record AdminNotice(Reason reason, AccountId account, List<Recipient> recipients) implements PersonalData {

    /** Checks every part is present, and copies the list. */
    public AdminNotice {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(account, "account");
        recipients = List.copyOf(recipients);
    }

    /**
     * A form that hides the recipients.
     *
     * @return {@code AdminNotice[redacted]}
     */
    @Override
    public String toString() {
        return "AdminNotice[redacted]";
    }

    /**
     * Why the Admins are told (FR-389).
     *
     * <p>Built in M6.
     */
    public enum Reason {
        /** An account gained the Admin role, when it was opened or since. */
        ADMIN_ROLE_GAINED,
        /** An Admin account was reactivated. */
        ADMIN_REACTIVATED,
        /** An Admin's password changed. */
        ADMIN_PASSWORD_CHANGED,
        /** An Admin's new email took effect. */
        ADMIN_EMAIL_CHANGED
    }

    /**
     * One active Admin the notice goes to. It holds an email address, so it prints redacted
     * (NFR-19).
     *
     * <p>Built in M6.
     *
     * @param email the Admin's address
     * @param language the language the Admin reads
     */
    public record Recipient(EmailAddress email, Language language) implements PersonalData {

        /** Checks every part is present. */
        public Recipient {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(language, "language");
        }

        /**
         * A form that hides the address.
         *
         * @return {@code AdminNotice.Recipient[redacted]}
         */
        @Override
        public String toString() {
            return "AdminNotice.Recipient[redacted]";
        }
    }
}
