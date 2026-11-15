/**
 * Access's published API (DL §2.2, §2.3 rule 4): the {@link
 * com.dispatchflow.access.application.api.Actor} the web adapter passes to every web-called use
 * case, and the questions and commands other modules send Access: an account's standing, a
 * session's contacts and validity, the company's profile, the push addresses of an account, and
 * removing the Driver role.
 *
 * <p>Its types use only kernel types, JDK types, their own records and enums, and Audit's {@code
 * CallContext}, never a domain type (DL §2.3 rule 1). The caller translates each answer into its
 * own values.
 */
@NullMarked
package com.dispatchflow.access.application.api;

import org.jspecify.annotations.NullMarked;
