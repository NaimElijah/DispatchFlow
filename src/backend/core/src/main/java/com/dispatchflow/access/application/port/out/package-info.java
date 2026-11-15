/**
 * Access's outbound ports (DL §3.6, TS §6.4): the repositories of its three aggregates, the
 * password hasher, the token maker and signer, the common-password list, the email sender and the
 * daily cap of recovery emails, the revocation cache, the push tokens, and the read port {@code
 * AccessViews} with its view records.
 * Adapters implement them.
 *
 * <p>The records here that carry a secret or personal data, the presented password and token, an
 * issued or signed token, a notice's recipients and the views, print redacted and are never logged
 * (NFR-19).
 */
@NullMarked
package com.dispatchflow.access.application.port.out;

import org.jspecify.annotations.NullMarked;
