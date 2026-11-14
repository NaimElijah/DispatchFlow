package com.dispatchflow.audit.domain;

import com.dispatchflow.shared.DomainError;
import java.util.Objects;

/**
 * How an audited attempt ended (NFR-93): it succeeded, or it was refused with the refusal's error
 * code. Refused attempts are audited too, such as a failed sign-in (NFR-23, ADR-110 point 1).
 */
public sealed interface AuditOutcome {

    /**
     * The attempt succeeded.
     *
     * @return the success outcome
     */
    static AuditOutcome succeeded() {
        return new Succeeded();
    }

    /**
     * The attempt was refused with a named domain error.
     *
     * @param error the error it was refused with; only its code is kept
     * @return the refusal outcome
     */
    static AuditOutcome refused(DomainError error) {
        return new Refused(ErrorCode.of(error));
    }

    /**
     * The attempt was refused with a code from outside the domain, such as {@code STALE_VERSION}.
     *
     * @param code the refusal's code
     * @return the refusal outcome
     */
    static AuditOutcome refused(ErrorCode code) {
        return new Refused(code);
    }

    /** The attempt succeeded, and its change is in the same transaction as the record (NFR-23). */
    record Succeeded() implements AuditOutcome {}

    /**
     * The attempt was refused.
     *
     * @param code the refusal's error code
     */
    record Refused(ErrorCode code) implements AuditOutcome {

        /** Checks the code is present. */
        public Refused {
            Objects.requireNonNull(code, "code");
        }
    }
}
