/**
 * Audit's outbound port: {@link com.dispatchflow.audit.application.port.out.AuditRecords}, the
 * append-only store of audit records and the database function that deletes the expired ones
 * (NFR-258, ADR-075). The persistence adapter implements it.
 */
@NullMarked
package com.dispatchflow.audit.application.port.out;

import org.jspecify.annotations.NullMarked;
