package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.NewPassword;
import com.dispatchflow.access.domain.account.PasswordHash;

/**
 * Hashes passwords and checks a presented one, with Spring Security's Argon2id encoder (NFR-36, TS
 * §6.4). Hashing needs a salt and is slow on purpose, so it stays out of the domain, which only
 * holds the {@code PasswordHash} (DL §3.9).
 *
 * <p>Sign-in runs the same hash work for an address no account holds as for a known one, through
 * {@link #checkDecoy} (NFR-225).
 *
 * <p>Built in M5.
 */
public interface PasswordHasher {

    /**
     * Hashes a new password.
     *
     * <p>Built in M6.
     *
     * @param password the password, checked against the policy (NFR-95)
     * @return its salted hash
     */
    PasswordHash hash(NewPassword password);

    /**
     * Whether a presented password is the one a hash was made from, in time that doesn't depend on
     * where they differ.
     *
     * <p>Built in M5.
     *
     * @param presented the password as typed
     * @param hash the stored hash
     * @return {@code true} when it matches
     */
    boolean matches(PresentedPassword presented, PasswordHash hash);

    /**
     * Checks a presented password against a decoy hash the adapter keeps, with the same hash work
     * as {@link #matches}, and never matches. Sign-in calls it for an address no account holds, so
     * its answer takes as long as for a known one (NFR-225).
     *
     * <p>Built in M5.
     *
     * @param presented the password typed for an address no account holds
     */
    void checkDecoy(PresentedPassword presented);
}
