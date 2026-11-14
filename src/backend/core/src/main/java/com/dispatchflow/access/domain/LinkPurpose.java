package com.dispatchflow.access.domain;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * What a credential link lets its holder do, and nothing else (FR-294, FR-345, FR-388). Only an
 * email-change link carries an address: the one it confirms.
 */
public sealed interface LinkPurpose {

    /**
     * The kind of link this purpose is sent as.
     *
     * @return the kind, which sets the lifetime and which older links a new one voids
     */
    LinkKind kind();

    /** Sets a new user's first password (FR-294). */
    record SetPassword() implements LinkPurpose {
        @Override
        public LinkKind kind() {
            return LinkKind.SET_PASSWORD;
        }
    }

    /** Sets a new password for a user who forgot theirs (FR-386, FR-345). */
    record Recovery() implements LinkPurpose {
        @Override
        public LinkKind kind() {
            return LinkKind.RECOVERY;
        }
    }

    /**
     * Confirms a new email address, which takes effect only when the link is used (FR-388). It holds
     * a person's address, so it is {@link PersonalData} and prints redacted (ADR-106).
     *
     * @param newEmail the address the link was sent to
     */
    record EmailChange(EmailAddress newEmail) implements LinkPurpose, PersonalData {

        /** Checks the address is present. */
        public EmailChange {
            Objects.requireNonNull(newEmail, "newEmail");
        }

        @Override
        public LinkKind kind() {
            return LinkKind.EMAIL_CHANGE;
        }

        /**
         * A form that hides the address.
         *
         * @return {@code EmailChange[redacted]}
         */
        @Override
        public String toString() {
            return "EmailChange[redacted]";
        }
    }
}
