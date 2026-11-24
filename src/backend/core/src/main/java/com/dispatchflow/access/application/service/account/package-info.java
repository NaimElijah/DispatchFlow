/**
 * Access's account use cases (DL §4.1): opening and changing accounts, their password, email,
 * language and terms, the server steps that create the first Admin and an Admin on a written
 * request, the account queries, and the implementation of the {@code Accounts} API, with the emails
 * their transactions leave to send. The credential-link use cases sit in {@code credentiallink}, the
 * account-state use cases in {@code state}, and the role use cases, the {@code RemoveDriverRole}
 * API's implementation among them, in {@code role}, beneath it (ADR-125); this package imports none
 * of them.
 */
@NullMarked
package com.dispatchflow.access.application.service.account;

import org.jspecify.annotations.NullMarked;
