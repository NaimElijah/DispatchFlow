/**
 * Access's credential-link use cases (DL §4.1, §17): an Admin sending a set-password link, a user
 * asking for a recovery link or an email change, setting a password or confirming an email through
 * the link, and the retention job's step that drops old links. A credential link belongs to the
 * {@code UserAccount} aggregate, so this package sits beneath the account's use cases (ADR-125).
 */
@NullMarked
package com.dispatchflow.access.application.service.account.credentiallink;

import org.jspecify.annotations.NullMarked;
