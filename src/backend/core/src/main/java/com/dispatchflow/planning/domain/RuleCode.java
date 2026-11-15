package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.MalformedRuleCode;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The rule a violation breaks, as the optimizer names it: a requirement row's ID such as {@code
 * BR-03} or {@code FR-91} (BR-16, BR-57). It is opaque, because the rules exist once, in the Python
 * validator (BR-42): Planning compares codes and shows them. It knows the two a hand-over reads,
 * {@link #SHIFTS} and {@link #BREAK_RESERVE} (FR-304), and the one it reports itself, {@link #LOCKS}
 * (FR-238).
 *
 * @param value the row ID
 */
public record RuleCode(String value) {

    private static final Pattern ROW_ID = Pattern.compile("[A-Z]{2,3}-[0-9]{1,4}");

    /** BR-06, routes stay within the driver's shift: the rule a hand-over may break (FR-304). */
    public static final RuleCode SHIFTS = new RuleCode("BR-06");

    /**
     * BR-105, the break reserve: a late return that breaks a shift breaks it too, so a confirmed
     * hand-over waives both (FR-304, ADR-112).
     */
    public static final RuleCode BREAK_RESERVE = new RuleCode("BR-105");

    /**
     * FR-147, a locked route kept as its lock says: the rule {@code Plan.locksBrokenBy} reports for
     * a lock a daily solve didn't keep, until the optimizer has a lock rule of its own (FR-238,
     * ADR-105, ADR-112).
     */
    public static final RuleCode LOCKS = new RuleCode("FR-147");

    /**
     * Checks the code.
     *
     * @throws RuleViolation with {@link MalformedRuleCode} when it is not a row ID
     */
    public RuleCode {
        Objects.requireNonNull(value, "value");
        if (!ROW_ID.matcher(value).matches()) {
            throw new RuleViolation(new MalformedRuleCode());
        }
    }

    /**
     * Creates the code.
     *
     * @param value the row ID
     * @return the code
     * @throws RuleViolation with {@link MalformedRuleCode} when it is not a row ID
     */
    public static RuleCode of(String value) {
        return new RuleCode(value);
    }
}
