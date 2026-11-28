/**
 * Who the caller is, for every request (ADR-140 decisions 3, 4 and 7, ADR-147). It sets the
 * request's one principal: from the stand-in session of a seeded account until sign-in is built,
 * then from the access token (#199). Its access interceptor builds each {@code /api} call's
 * caller, the only place an {@code Actor} is built, and answers 401 {@code ACCESS_TOKEN_REFUSED},
 * with exactly {@code WWW-Authenticate: Bearer}, to a call with no principal; #171 adds the role
 * check's 403 there (ADR-068). Its guard refuses to boot with the stand-in's configuration under
 * any profiles but exactly {@code dev} or exactly {@code test}, or in a deployed environment.
 */
@NullMarked
package com.dispatchflow.web.security;

import org.jspecify.annotations.NullMarked;
