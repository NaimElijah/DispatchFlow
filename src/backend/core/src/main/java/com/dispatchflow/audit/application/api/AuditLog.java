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
 * transaction has rolled back. The use case records the refusal that leaves its {@code Change}
 * through {@link Refusals}, and a conflict it answers itself directly. The adapter that refused a
 * call no use case saw records that one: a 401, a lost race, or a changing call that breaks the
 * contract (ADR-129 decision 5, ADR-149, ADR-150).
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
     * @throws IllegalStateException when the call's operation or trace ID is malformed, or too
     *     many records are named: a server's bug, so its cause is Audit's {@code RuleViolation}, with
     *     {@code MalformedOperationName}, {@code InvalidTraceId} or {@code TooManyNamedRecords}, and
     *     never one a use case would take for its change's refusal (ADR-149); a client version that
     *     doesn't parse leaves the client out of the record instead (ADR-110)
     * @throws RuntimeException when called inside a read-only transaction, which would never write
     *     the record, so it fails rather than lose it (ADR-149 decision 3)
     */
    void succeeded(CallContext call, Instant at, List<AuditedRecord> records);

    /**
     * Records a refused attempt in a transaction of its own, so the refusal's rollback doesn't lose
     * it (NFR-23, ADR-110 point 1). It is called once the refused transaction has rolled back, with
     * no transaction open, so the one it opens is fresh (ADR-129 decision 5, ADR-149); a use case
     * calls it through {@link Refusals}, which runs its {@code Change} (ADR-150). Called
     * inside an open transaction, it joins it, and a rollback there loses the record, so no
     * transactional class calls it ({@code TransactionRules} rule 10) but a {@code Change} that
     * records a refusal raised before it changed anything and lets none leave, so that its
     * transaction commits the record; that rule names each such {@code Change}.
     *
     * @param call who made the attempt, through which client, as which operation
     * @param at when the attempt was made
     * @param records the records the operation names, in order; possibly none, and at most 500
     * @param errorCode the refusal's stable code: a domain error's code, or one the adapters answer
     *     with, such as {@code STALE_VERSION}; never its parameters
     * @throws IllegalStateException when a part is malformed: a server's bug, so its cause is
     *     Audit's {@code RuleViolation}, with {@code MalformedOperationName}, {@code InvalidTraceId},
     *     {@code TooManyNamedRecords} or {@code MalformedErrorCode} (ADR-149)
     * @throws RuntimeException when called inside a read-only transaction, which would never write
     *     the record, so it fails rather than lose it (ADR-149 decision 3)
     */
    void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode);
}
