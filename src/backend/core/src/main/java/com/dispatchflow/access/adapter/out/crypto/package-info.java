/**
 * Access's hashing and signing adapter (ADR-130): implements {@code PasswordHasher} with Spring
 * Security's Argon2id encoder, {@code TokenSigner} with ES256 JWTs through Nimbus JOSE + JWT, and
 * {@code SecretTokens} from a secure random source.
 */
@NullMarked
package com.dispatchflow.access.adapter.out.crypto;

import org.jspecify.annotations.NullMarked;
