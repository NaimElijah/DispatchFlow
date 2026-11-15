/**
 * Audit's published API (DL §2.1, §2.3 rule 4): the {@link
 * com.dispatchflow.audit.application.api.AuditLog} port every changing use case writes through, in
 * the same transaction as its change (NFR-23, TS §6.2), and the values it takes: the {@link
 * com.dispatchflow.audit.application.api.CallContext} of a call or a server step, and the {@link
 * com.dispatchflow.audit.application.api.AuditedRecord}s it names.
 *
 * <p>Its types hold only kernel IDs, JDK types and their own enums, never an {@code Actor}, so
 * Access may depend on Audit without a cycle (DL §2.1, ADR-040). They carry no personal data
 * (NFR-94).
 */
@NullMarked
package com.dispatchflow.audit.application.api;

import org.jspecify.annotations.NullMarked;
