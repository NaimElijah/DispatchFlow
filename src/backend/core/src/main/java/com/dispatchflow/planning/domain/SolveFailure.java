package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.SolveFailureReason;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Why a solve job failed, with the violations that refused its solution where there are some (FR-65,
 * BR-57, FR-238).
 *
 * @param reason the reason
 * @param violations the violations, present exactly for a rejected solution or an unhonoured lock
 */
public record SolveFailure(SolveFailureReason reason, List<Violation> violations) {

    private static final Set<SolveFailureReason> WITH_VIOLATIONS =
            EnumSet.of(SolveFailureReason.VALIDATOR_REJECTED, SolveFailureReason.LOCK_NOT_HONOURED);

    private static final String VIOLATIONS_MATCH_REASON =
            "violations are listed exactly for a rejection or an unhonoured lock";

    /**
     * Checks the violations match the reason. A worker builds a failure through the factories below,
     * and persistence through {@link #ofStored}, so a mismatch here is a bug in the caller.
     *
     * @throws AssertionError when violations are missing for, or present without, a reason that
     *     lists them
     */
    public SolveFailure {
        Objects.requireNonNull(reason, "reason");
        violations = List.copyOf(violations);
        if (!violationsMatch(reason, violations)) {
            throw new AssertionError(VIOLATIONS_MATCH_REASON);
        }
    }

    /**
     * Rebuilds a stored failure; only the persistence adapter calls it, so a damaged row is an
     * incident rather than a bug in a worker.
     *
     * @param reason the stored reason
     * @param violations the stored violations
     * @return the failure
     * @throws StoredStateInvalid when violations are missing for, or present without, a reason that
     *     lists them
     */
    public static SolveFailure ofStored(SolveFailureReason reason, List<Violation> violations) {
        if (!violationsMatch(Objects.requireNonNull(reason, "reason"), violations)) {
            throw new StoredStateInvalid(SolveFailure.class, VIOLATIONS_MATCH_REASON);
        }
        return new SolveFailure(reason, violations);
    }

    /**
     * A failure with no violations to list, such as an overrun or an unreachable optimizer.
     *
     * @param reason the reason; neither {@code VALIDATOR_REJECTED} nor {@code LOCK_NOT_HONOURED}
     * @return the failure
     * @throws AssertionError for a reason that lists violations
     */
    public static SolveFailure of(SolveFailureReason reason) {
        return new SolveFailure(reason, List.of());
    }

    /**
     * The validator rejected the solution (BR-57).
     *
     * @param violations every violation it found, at least one
     * @return the failure
     * @throws AssertionError when there is none
     */
    public static SolveFailure rejected(List<Violation> violations) {
        return new SolveFailure(SolveFailureReason.VALIDATOR_REJECTED, violations);
    }

    /**
     * A locked route could not be kept (FR-238), as {@code Plan.locksBrokenBy} names them.
     *
     * @param violations the violations naming each such route, at least one
     * @return the failure
     * @throws AssertionError when there is none
     */
    public static SolveFailure lockNotHonoured(List<Violation> violations) {
        return new SolveFailure(SolveFailureReason.LOCK_NOT_HONOURED, violations);
    }

    private static boolean violationsMatch(SolveFailureReason reason, List<Violation> violations) {
        return WITH_VIOLATIONS.contains(reason) != violations.isEmpty();
    }
}
