package com.dispatchflow.shared;

import java.util.List;
import java.util.Objects;

/**
 * Thrown where the requirements say to list every refusal at once (DL §3.8): a summary error that
 * names what was refused, and each refusal under it. Publishing's summary is {@code
 * PublishingRefused(step)} with that step's refusals (BR-72); an import's is {@code
 * ImportHasErrors} with one error per row.
 *
 * <p>It is built only through {@link #throwIfAny}, so a list with no refusals can't exist.
 */
public final class RuleViolations extends RuntimeException {

    private final DomainError summary;
    private final List<DomainError> errors;

    private RuleViolations(DomainError summary, List<DomainError> errors) {
        super(summary.code());
        this.summary = summary;
        this.errors = errors;
    }

    /**
     * Throws the refusals, if there are any, and otherwise returns.
     *
     * @param summary what was refused
     * @param errors every refusal found, in the order they should be shown
     * @throws RuleViolations when {@code errors} is not empty
     */
    public static void throwIfAny(DomainError summary, List<? extends DomainError> errors) {
        Objects.requireNonNull(summary, "summary");
        List<DomainError> found = List.copyOf(errors);
        if (!found.isEmpty()) {
            throw new RuleViolations(summary, found);
        }
    }

    /**
     * What was refused.
     *
     * @return the summary error
     */
    public DomainError summary() {
        return summary;
    }

    /**
     * Every refusal, in the order given.
     *
     * @return an unmodifiable, non-empty list
     */
    public List<DomainError> errors() {
        return errors;
    }
}
