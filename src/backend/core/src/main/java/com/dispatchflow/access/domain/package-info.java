/**
 * The Access context's domain (DL §4.1): the company profile, user accounts with their roles and
 * credential links, and sessions with their refresh tokens.
 *
 * <p>Each aggregate has its own package: {@code account}, {@code session} and {@code
 * companyprofile}. This package holds the module's error ({@link
 * com.dispatchflow.access.domain.AccessError}) and the values that several of them use or that no
 * aggregate uses (ADR-118). It imports none of its subpackages.
 *
 * <p>It is pure (DL §3.9): it hashes no password and signs no token. The hashing and signing
 * adapters hand it their results as opaque values ({@link
 * com.dispatchflow.access.domain.account.PasswordHash} and {@link
 * com.dispatchflow.access.domain.TokenHash}), and it never reads the bundled list of common
 * passwords, which arrives as a {@link com.dispatchflow.access.domain.CommonPasswords}.
 */
@NullMarked
package com.dispatchflow.access.domain;

import org.jspecify.annotations.NullMarked;
