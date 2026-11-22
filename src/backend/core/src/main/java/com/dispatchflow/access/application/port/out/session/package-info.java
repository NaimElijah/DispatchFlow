/**
 * The outbound ports that only Access's session use cases use (DL §4.1, TS §6.4): the {@code
 * TokenSigner} that signs access tokens, with the {@code AccessClaims} it signs and the {@code
 * SignedToken} it gives back (ADR-045, ADR-069), and the {@code PushTokens} of signed-in phones
 * (ADR-083). Adapters implement them. A signed token is a secret: it prints redacted and is never
 * logged (NFR-19).
 */
@NullMarked
package com.dispatchflow.access.application.port.out.session;

import org.jspecify.annotations.NullMarked;
