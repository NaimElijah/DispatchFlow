package com.dispatchflow.access.application.port.out;

/**
 * Signs access tokens as ES256 JWTs with Nimbus JOSE + JWT (ADR-045, ADR-069, TS §6.4). The signing
 * key is a secret and stays in the adapter.
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
