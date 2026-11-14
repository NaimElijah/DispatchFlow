package com.dispatchflow.audit.domain;

import com.dispatchflow.shared.DomainError;

/**
 * The Audit context's refusals (DL §4.8, ADR-110). None names the refused text, because an
 * operation name, an error code, an app version or a trace ID that fails its check may hold
 * anything (NFR-94, ADR-043).
 */
public sealed interface AuditError extends DomainError {

    /**
     * An operation that is not an ASCII identifier of at most 100 characters, such as {@code
     * publishPlan} or {@code publish-plan}.
     */
    record MalformedOperationName() implements AuditError {}

    /** A refusal's code that is not upper snake case ASCII, such as {@code STALE_VERSION}. */
    record MalformedErrorCode() implements AuditError {}

    /**
     * An app version that is not three dot-separated numbers without leading zeros, each within
     * an {@code int} (ADR-082).
     */
    record InvalidAppVersion() implements AuditError {}

    /** A trace ID that is not a W3C trace-id: 32 lower-case hex digits, not all zero (NFR-20). */
    record InvalidTraceId() implements AuditError {}
}
