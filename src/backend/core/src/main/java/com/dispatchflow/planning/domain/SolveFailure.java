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

    /**
     * Checks the violations match the reason. Only the factories below build one, so a mismatch can
     * only be damaged stored state.
     *
     * @throws StoredStateInvalid when violations are missing for, or present without, a reason that
     *     lists them
     */
    public SolveFailure {
        Objects.requireNonNull(reason, "reason");
        violations = List.copyOf(violations);
        if (WITH_VIOLATIONS.contains(reason) == violations.isEmpty()) {
            throw new StoredStateInvalid(
                    SolveFailure.class, "violations are listed exactly for a rejection or an unhonoured lock");
        }
    }

    /**
     * A failure with no violations to list, such as an overrun or an unreachable optimizer.
     *
     * @param reason the reason; neither {@code VALIDATOR_REJECTED} nor {@code LOCK_NOT_HONOURED}
     * @return the failure
     */
    public static SolveFailure of(SolveFailureReason reason) {
        return new SolveFailure(reason, List.of());
    }

    /**
     * The validator rejected the solution (BR-57).
     *
     * @param violations every violation it found, at least one
     * @return the failure
     */
    public static SolveFailure rejected(List<Violation> violations) {
        return new SolveFailure(SolveFailureReason.VALIDATOR_REJECTED, violations);
    }

    /**
     * A locked route could not be kept (FR-238).
     *
     * @param violations the violations naming each such route, at least one
     * @return the failure
     */
    public static SolveFailure lockNotHonoured(List<Violation> violations) {
        return new SolveFailure(SolveFailureReason.LOCK_NOT_HONOURED, violations);
    }
}
