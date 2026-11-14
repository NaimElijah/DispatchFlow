package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.InvalidTraceId;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The trace ID of the request an audit record describes, the same ID as in its log lines and
 * client error reports (NFR-93, NFR-20, NFR-74). It is a W3C trace-context trace-id (TS §16,
 * Correlation): 32 lower-case hex digits, which the W3C format forbids to be all zero.
 *
 * @param value the trace-id
 */
public record TraceId(String value) {

    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern ALL_ZERO = Pattern.compile("0{32}");

    /**
     * Checks the trace ID.
     *
     * @throws RuleViolation with {@link InvalidTraceId} when it is not 32 lower-case hex digits,
     *     or is all zero
     */
    public TraceId {
        Objects.requireNonNull(value, "value");
        if (!TRACE_ID.matcher(value).matches() || ALL_ZERO.matcher(value).matches()) {
            throw new RuleViolation(new InvalidTraceId());
        }
    }

    /**
     * Creates the trace ID.
     *
     * @param value the trace-id
     * @return the trace ID
     * @throws RuleViolation with {@link InvalidTraceId} when it is not a W3C trace-id
     */
    public static TraceId of(String value) {
        return new TraceId(value);
    }
}
