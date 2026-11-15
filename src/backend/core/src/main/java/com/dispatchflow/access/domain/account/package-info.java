/**
 * The user account aggregate (DL §4.1): {@link com.dispatchflow.access.domain.account.UserAccount}
 * with its credential links, its snapshot and states, and the values only it uses: email addresses,
 * password hashes, roles, grants and capabilities. The account, its credential links and its
 * check on the active Admins share this package because the account reaches them through
 * package-private members (ADR-118).
 */
@NullMarked
package com.dispatchflow.access.domain.account;

import org.jspecify.annotations.NullMarked;
