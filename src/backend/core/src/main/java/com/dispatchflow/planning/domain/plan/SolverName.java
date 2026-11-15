package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.PlanningError.InvalidSolverName;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The solver a solve runs with. Outside the optimization service a solver is known only by its name,
 * so adding one changes nothing here (FR-20, FR-224, ADR-101).
 *
 * @param value the name: a lower-case letter, then up to 39 lower-case letters, digits or
 *     underscores
 */
public record SolverName(String value) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,39}");

    /** OR-Tools, the default solver (FR-20). */
    public static final SolverName OR_TOOLS = new SolverName("ortools");

    /**
     * Checks the name.
     *
     * @throws RuleViolation with {@link InvalidSolverName} when it is not such a name
     */
    public SolverName {
        Objects.requireNonNull(value, "value");
        if (!NAME.matcher(value).matches()) {
            throw new RuleViolation(new InvalidSolverName());
        }
    }

    /**
     * Creates the name.
     *
     * @param value the name
     * @return the solver name
     * @throws RuleViolation with {@link InvalidSolverName} when it is not such a name
     */
    public static SolverName of(String value) {
        return new SolverName(value);
    }
}
