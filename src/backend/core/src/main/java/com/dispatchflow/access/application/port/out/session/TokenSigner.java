package com.dispatchflow.access.application.port.out.session;

/**
 * Signs access tokens as ES256 JWTs with Nimbus JOSE + JWT (ADR-045, ADR-069, TS §6.4). The signing
 * key is a secret and stays in the adapter. It signs whatever {@link AccessClaims#expiresAt()} the
 * use case set: until M6 builds refresh, the {@code dev} profile alone may set a longer
 * access-token lifetime (ADR-121). Unless {@code dev} is the only active profile, the core refuses
 * to boot with a lifetime other than NFR-86's 15 minutes, which M5 tests. M6 deletes the dev
 * allowance, so from then on every profile refuses any other lifetime.
 *
 * <p>Built in M5 and M13, method by method.
 */
public interface TokenSigner {

    /**
     * Signs an access token.
     *
     * <p>Built in M5.
     *
     * @param claims what the token says
     * @return the signed token
     */
    SignedToken sign(AccessClaims claims);

    /**
     * Replaces the signing key with a new one after a restore, and removes the old key from the
     * accepted set at once, with no overlap of two keys, so every access token signed before is
     * refused (NFR-272, ADR-045). ADR-069 allows an overlap only for an ordinary rotation, which
     * this is not.
     *
     * <p>Built in M13.
     */
    void rotateKey();
}
