package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.MalformedErrorCode;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The stable code of the refusal a refused attempt met (NFR-93, ADR-110): a domain error's code
 * (NFR-69, NFR-144), or one the adapters answer with, such as {@code STALE_VERSION}. The audit
 * record keeps the code alone and never the error's parameters.
 *
 * @param value the upper snake case code
 */
public record ErrorCode(String value) {

    private static final Pattern UPPER_SNAKE_CASE = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");

    /**
     * Checks the code.
     *
     * @throws RuleViolation with {@link MalformedErrorCode} when it is not upper snake case ASCII:
     *     words of capitals and digits, the first starting with a capital, joined by single
     *     underscores
     */
    public ErrorCode {
        Objects.requireNonNull(value, "value");
        if (!UPPER_SNAKE_CASE.matcher(value).matches()) {
            throw new RuleViolation(new MalformedErrorCode());
        }
    }

    /**
     * Creates the code of a refusal outside the domain, such as {@code STALE_VERSION}.
     *
     * @param value the code
     * @return the code
     * @throws RuleViolation with {@link MalformedErrorCode} when it is not upper snake case ASCII
     */
    public static ErrorCode of(String value) {
        return new ErrorCode(value);
    }

    /**
     * Takes a domain error's stable code, leaving its parameters behind.
     *
     * @param error the named error the attempt was refused with
     * @return its code
     */
    public static ErrorCode of(DomainError error) {
        return new ErrorCode(Objects.requireNonNull(error, "error").code());
    }
}
