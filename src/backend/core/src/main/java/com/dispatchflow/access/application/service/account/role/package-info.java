/**
 * The use cases that change an account's roles (DL §4.1, §17): an Admin's change of a user's roles,
 * and Fleet's removal of the Driver role when a driver retires, which keeps the account's other
 * roles. Each revokes the account's tokens with the change, so it evicts its cached generation after
 * the commit. They sit beneath the account's use cases (ADR-125).
 */
@NullMarked
package com.dispatchflow.access.application.service.account.role;

import org.jspecify.annotations.NullMarked;
