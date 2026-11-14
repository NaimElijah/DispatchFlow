package com.dispatchflow.shared;

import java.util.Objects;

/**
 * Thrown when stored state can't become a valid object (DL §3.5): an aggregate's {@code restore}
 * found a broken invariant at rest, or a stored kernel value, such as a {@link Version} or an
 * {@link EventId}, is malformed. It is not a {@link DomainError}, because no user's action caused
 * it and no user can fix it: a damaged row is an incident.
 *
 * <p>The detail names the broken rule, never the stored values, so it carries no personal data
 * into a log.
 */
public final class StoredStateInvalid extends RuntimeException {

    private final String subject;
    private final String detail;

    /**
     * Creates the failure.
     *
     * @param subject the aggregate or stored value that could not be rebuilt
     * @param detail the rule the stored state breaks, with no personal data
     */
    public StoredStateInvalid(Class<?> subject, String detail) {
        super(message(subject, detail));
        this.subject = subject.getSimpleName();
        this.detail = detail;
    }

    /**
     * Creates the failure, keeping the exception that showed the state is malformed.
     *
     * @param subject the aggregate or stored value that could not be rebuilt
     * @param detail the rule the stored state breaks, with no personal data
     * @param cause the exception raised while reading the stored state
     */
    public StoredStateInvalid(Class<?> subject, String detail, Throwable cause) {
        super(message(subject, detail), Objects.requireNonNull(cause, "cause"));
        this.subject = subject.getSimpleName();
        this.detail = detail;
    }

    private static String message(Class<?> subject, String detail) {
        return Objects.requireNonNull(subject, "subject").getSimpleName() + ": "
                + Objects.requireNonNull(detail, "detail");
    }

    /**
     * The aggregate or value that could not be rebuilt.
     *
     * @return its simple type name, such as {@code Plan}
     */
    public String subject() {
        return subject;
    }

    /**
     * The rule the stored state breaks.
     *
     * @return the detail given when this was thrown
     */
    public String detail() {
        return detail;
    }
}
