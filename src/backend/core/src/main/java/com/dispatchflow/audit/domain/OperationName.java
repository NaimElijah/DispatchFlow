package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.MalformedOperationName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The operation an audit record describes (NFR-93): the {@code client-api} operation ID of the call,
 * such as {@code publishPlan} or {@code signIn}, or the name of a server step that no call makes
 * (ADR-110). An Admin filters audit records by it (FR-126). It is an ASCII identifier, never free
 * text, so it can carry no personal data (NFR-94). The pattern admits any operation ID style a
 * contract may adopt, so a later contract can't make every audit write refuse.
 *
 * @param value the identifier
 */
public record OperationName(String value) {

    /** The longest operation name accepted, in characters. */
    public static final int MAX_LENGTH = 100;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0," + (MAX_LENGTH - 1) + "}");

    /**
     * Checks the name.
     *
     * @throws RuleViolation with {@link MalformedOperationName} when it is not an ASCII letter
     *     followed by letters, digits, dots, underscores or hyphens, or is longer than {@link
     *     #MAX_LENGTH}
     */
    public OperationName {
        Objects.requireNonNull(value, "value");
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new RuleViolation(new MalformedOperationName());
        }
    }

    /**
     * Creates the name.
     *
     * @param value the operation ID or step name
     * @return the name
     * @throws RuleViolation with {@link MalformedOperationName} when it is not an ASCII identifier
     *     of at most {@link #MAX_LENGTH} characters
     */
    public static OperationName of(String value) {
        return new OperationName(value);
    }
}
