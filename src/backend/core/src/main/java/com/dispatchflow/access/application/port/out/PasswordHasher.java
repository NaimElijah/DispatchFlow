package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.NewPassword;
import com.dispatchflow.access.domain.account.PasswordHash;

/**
 * Hashes passwords and checks a presented one, with Spring Security's Argon2id encoder (NFR-36, TS
 * §6.4). Hashing needs a salt and is slow on purpose, so it stays out of the domain, which only
 * holds the {@code PasswordHash} (DL §3.9).
 *
 * <p>Sign-in runs the same hash work for an address no account holds, and for an account with no
 * password hash yet, as for an account with one, through {@link #checkDecoy} (NFR-145, NFR-225).
 *
 * <p>To Be Implemented.
 */
public interface PasswordHasher {

    /**
     * Hashes a new password.
     *
     * <p>To Be Implemented.
     *
     * @param password the password, checked against the policy (NFR-95)
     * @return its salted hash
     */
    PasswordHash hash(NewPassword password);

    /**
     * Whether a presented password is the one a hash was made from, in time that doesn't depend on
     * where they differ.
     *
     * <p>To Be Implemented.
     *
     * @param presented the password as typed
     * @param hash the stored hash
     * @return {@code true} when it matches
     */
    boolean matches(PresentedPassword presented, PasswordHash hash);

    /**
     * Checks a presented password against a decoy hash the adapter keeps, with the same hash work
     * as {@link #matches}, and never matches. Sign-in calls it for an address no account holds and
     * for an account with no password hash, whose set-password link was never used (FR-294), so its
     * answer takes as long as for an account with a password, and a pending account can't be told
     * from an unknown address (NFR-145, NFR-225).
     *
     * <p>To Be Implemented.
     *
     * @param presented the password typed for an address no account holds, or for an account with
     *     no password hash
     */
    void checkDecoy(PresentedPassword presented);
}
