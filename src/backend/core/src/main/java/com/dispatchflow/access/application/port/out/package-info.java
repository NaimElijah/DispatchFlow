/**
 * Access's outbound ports (DL §3.6, TS §6.4): the repositories of its three aggregates, the
 * password hasher, the token maker, the common-password list, the email sender and the daily cap of
 * recovery emails, the revocation cache, and the read port {@code AccessViews} with its view
 * records. The token signer and the push tokens, which only the session use cases use, sit in
 * {@code session} (ADR-125); this root imports none of them. Adapters implement them.
 *
 * <p>The records here that carry a secret or personal data, the presented password and token, an
 * issued token, a notice's recipients and the views, print redacted and are never logged
 * (NFR-19).
 */
@NullMarked
package com.dispatchflow.access.application.port.out;

import org.jspecify.annotations.NullMarked;
