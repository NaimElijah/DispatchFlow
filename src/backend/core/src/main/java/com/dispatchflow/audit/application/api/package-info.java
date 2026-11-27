/**
 * Audit's published API (DL §2.1, §2.3 rule 4): the {@link
 * com.dispatchflow.audit.application.api.AuditLog} port every changing use case writes through, a
 * success in the same transaction as its change and a refusal once that transaction has rolled back
 * (NFR-23, TS §6.2, ADR-149); the {@link com.dispatchflow.audit.application.api.Refusals} helper,
 * which runs a use case's attempt and records the refusal that leaves it (ADR-150); and the values
 * the port takes: the {@link com.dispatchflow.audit.application.api.CallContext} of a call or a
 * server step, and the {@link com.dispatchflow.audit.application.api.AuditedRecord}s it names.
 *
 * <p>Its types hold only kernel IDs, JDK types and their own enums, never an {@code Actor}, so
 * Access may depend on Audit without a cycle (DL §2.1, ADR-040). They carry no personal data
 * (NFR-94).
 */
@NullMarked
package com.dispatchflow.audit.application.api;

import org.jspecify.annotations.NullMarked;
