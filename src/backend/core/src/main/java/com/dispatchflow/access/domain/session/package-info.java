/**
 * The session aggregate (DL §4.1): {@link com.dispatchflow.access.domain.session.Session}, one
 * signed-in device, with its chain of refresh tokens, its snapshot, states and generation, and the
 * outcome of exchanging a refresh token. The session and its refresh tokens share this package
 * because the session reaches its tokens through package-private members (ADR-118).
 */
@NullMarked
package com.dispatchflow.access.domain.session;

import org.jspecify.annotations.NullMarked;
