/**
 * Access's account use cases (DL §4.1): opening and changing accounts, their roles, password,
 * email, language and terms, the server steps that create the first Admin and an Admin on a
 * written request, the account queries, and the implementations of the {@code Accounts} and {@code RemoveDriverRole}
 * APIs, with the emails their transactions leave to send. The credential-link use cases sit in
 * {@code credentiallink} and the account-state use cases in {@code state}, beneath it (ADR-125);
 * this package imports none of them.
 */
@NullMarked
package com.dispatchflow.access.application.service.account;

import org.jspecify.annotations.NullMarked;
