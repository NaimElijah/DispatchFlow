package com.dispatchflow.audit.application.api;

import java.time.Instant;
import java.util.List;

/**
 * The audit trail every changing API call, sign-in, refresh and sign-out writes to, whether it
 * succeeded or was refused (NFR-23, NFR-93, ADR-110). Every module but Audit depends on it (DL
 * §2.1). It holds only IDs, codes, enums and instants, so no personal data or secret reaches a
 * record (NFR-94).
 *
 * <p>A success is recorded inside its change's transaction, and a refusal after the refused
 * transaction has rolled back: by the use case that called its {@code Change}, or by the adapter
 * that refused a call no use case saw, such as a 401 or a lost race (ADR-129 decision 5,
 * ADR-149).
 */
public interface AuditLog {

    /**
     * Records a successful attempt, in the transaction of the change it describes, so the record
     * and the change commit together, or neither does (NFR-23, TS §6.2). Only a transactional
     * class calls it ({@code TransactionRules} rule 9, ADR-149).
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
     * it (NFR-23, ADR-110 point 1). It is called once the refused transaction has rolled back, with
     * no transaction open, so the one it opens is fresh (ADR-129 decision 5, ADR-149). Called
     * inside an open transaction, it joins it, and a rollback there loses the record; the one case
     * that does so on purpose is a {@code Change} that records a refusal raised before it changed
     * anything and lets none leave, so its transaction commits the record alone.
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
