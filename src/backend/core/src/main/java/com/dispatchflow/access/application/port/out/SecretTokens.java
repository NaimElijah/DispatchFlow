package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.TokenHash;

/**
 * Makes the opaque tokens of refresh tokens and credential links, and hashes a presented one
 * (NFR-91, NFR-211, ADR-045): 256 bits from a secure random source, hashed with SHA-256. Randomness
 * stays out of the domain, which only compares {@code TokenHash}es (DL §3.9).
 *
 * <p>To Be Implemented.
 */
public interface SecretTokens {

    /**
     * Makes a new token.
     *
     * <p>To Be Implemented.
     *
     * @return the token and its hash
     */
    IssuedToken issue();

    /**
     * Hashes a presented token, for the lookup and the domain's comparison.
     *
     * <p>To Be Implemented.
     *
     * @param token the token a client presented
     * @return its hash
     */
    TokenHash hashOf(PresentedToken token);
}
