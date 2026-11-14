/**
 * The Audit context's domain (DL §4.8): the audit record every changing API call, sign-in, refresh
 * and sign-out leaves (NFR-23), with exactly NFR-93's fields, its one-year retention (NFR-258) and
 * its named errors ({@link com.dispatchflow.audit.domain.AuditError}). ADR-110 records the rules
 * no document settled.
 *
 * <p>An audit record is a record, not an aggregate (DL §3.2): it is immutable, has no version and
 * raises no event, so the context has no {@code events} package. Every field is an ID, a code, an
 * enum, a number or an instant, so no personal data or secret can reach one (NFR-94).
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify and the shared kernel.
 */
@NullMarked
package com.dispatchflow.audit.domain;

import org.jspecify.annotations.NullMarked;
