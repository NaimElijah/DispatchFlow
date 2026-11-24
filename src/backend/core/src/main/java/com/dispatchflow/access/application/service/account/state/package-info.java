/**
 * The use cases that move an account between its states (DL §4.1): an Admin deactivating and
 * reactivating a user, the written-request step that deactivates an Admin, and the retention job's
 * step that anonymizes a deactivated account. They change {@code UserAccount}'s {@code
 * AccountState}, so this package sits beneath the account's use cases (ADR-125).
 */
@NullMarked
package com.dispatchflow.access.application.service.account.state;

import org.jspecify.annotations.NullMarked;
