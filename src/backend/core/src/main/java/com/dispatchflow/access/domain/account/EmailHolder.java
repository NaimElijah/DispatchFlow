package com.dispatchflow.access.domain.account;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * Which account, if any, holds an email address now: the fact the use case reads, by the unique
 * index on the canonical address, before it opens an account or confirms a new address (BR-45,
 * FR-392, DL §5). The index backs the rule when two requests race.
 *
 * <p>It names the address it was read for, so the account can check it was given the answer for
 * the right address. That makes it {@link PersonalData}, and it prints redacted (ADR-106).
 */
public sealed interface EmailHolder extends PersonalData {

    /**
     * No account holds the address.
     *
     * @param email the address looked up
     * @return the holder that is no one
     */
    static EmailHolder nobody(EmailAddress email) {
        return new Nobody(email);
    }

    /**
     * An account holds the address.
     *
     * @param email the address looked up
     * @param account the account holding it
     * @return the holder
     */
    static EmailHolder account(EmailAddress email, AccountId account) {
        return new HeldBy(email, account);
    }

    /**
     * The address this answer is for.
     *
     * @return the address looked up
     */
    EmailAddress email();

    /**
     * Whether an account other than the given one holds the address.
     *
     * @param account the account asking
     * @return {@code true} when another account holds it
     */
    boolean isHeldByAnotherThan(AccountId account);

    /**
     * Checks this answer is for the address the caller means.
     *
     * @param expected the address the caller is about to give an account
     * @throws AssertionError when the use case looked up another address: a bug, not a refusal
     */
    default void checkIsFor(EmailAddress expected) {
        if (!email().equals(expected)) {
            throw new AssertionError("the email holder was read for another address");
        }
    }

    /**
     * No account holds the address.
     *
     * @param email the address looked up
     */
    record Nobody(EmailAddress email) implements EmailHolder {

        /** Checks the address is present. */
        public Nobody {
            Objects.requireNonNull(email, "email");
        }

        @Override
        public boolean isHeldByAnotherThan(AccountId account) {
            return false;
        }

        /**
         * A form that hides the address.
         *
         * @return {@code EmailHolder.Nobody[redacted]}
         */
        @Override
        public String toString() {
            return "EmailHolder.Nobody[redacted]";
        }
    }

    /**
     * An account holds the address.
     *
     * @param email the address looked up
     * @param account the account that holds it
     */
    record HeldBy(EmailAddress email, AccountId account) implements EmailHolder {

        /** Checks both parts are present. */
        public HeldBy {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(account, "account");
        }

        @Override
        public boolean isHeldByAnotherThan(AccountId other) {
            return !account.equals(other);
        }

        /**
         * A form that hides the address.
         *
         * @return {@code EmailHolder.HeldBy[redacted]}
         */
        @Override
        public String toString() {
            return "EmailHolder.HeldBy[redacted]";
        }
    }
}
