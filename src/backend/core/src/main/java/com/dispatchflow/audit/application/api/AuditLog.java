package com.dispatchflow.audit.application.api;

import java.time.Instant;
import java.util.List;

/**
 * The audit trail every changing API call, sign-in, refresh and sign-out writes to, whether it
 * succeeded or was refused (NFR-23, NFR-93, ADR-110). Every module but Audit depends on it (DL
 * §2.1). It holds only IDs, codes, enums and instants, so no personal data or secret reaches a
 * record (NFR-94).
 *
 * <p>Built in M0.
 */
public interface AuditLog {

    /**
     * Records a successful attempt, in the transaction of the change it describes, so the record
     * and the change commit together (NFR-23, TS §6.2).
     *
     * <p>Built in M0.
     *
     * @param call who made the attempt, through which client, as which operation
     * @param at when the attempt was made
     * @param records the records the operation names, in order; possibly none, and at most 500
     * @throws com.dispatchflow.shared.RuleViolation with Audit's {@code MalformedOperationName},
     *     {@code InvalidTraceId} or {@code TooManyNamedRecords} when the call's operation or trace
     *     ID is malformed or too many records are named; a client version that doesn't parse
     *     leaves the client out of the record instead (ADR-110)
     */
    void succeeded(CallContext call, Instant at, List<AuditedRecord> records);

    /**
     * Records a refused attempt in a transaction of its own, so the refusal's rollback doesn't lose
     * it (NFR-23, ADR-110 point 1).
     *
     * <p>Built in M0.
     *
     * @param call who made the attempt, through which client, as which operation
     * @param at when the attempt was made
     * @param records the records the operation names, in order; possibly none, and at most 500
     * @param errorCode the refusal's stable code: a domain error's code, or one the adapters answer
     *     with, such as {@code STALE_VERSION}; never its parameters
     * @throws com.dispatchflow.shared.RuleViolation with Audit's {@code MalformedOperationName},
     *     {@code InvalidTraceId}, {@code TooManyNamedRecords} or {@code MalformedErrorCode} when a
     *     part is malformed
     */
    void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode);
}
