package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.shared.DomainAssertions.assertAllRefused;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * A refused command leaves a plan as it was: the same snapshot, no pending event and no pending
 * revision, because every rule is checked before the state changes (DL §3.10).
 */
final class PlanAssertions {

    private PlanAssertions() {}

    static void assertPlanRefused(Plan plan, ThrowingCallable call, DomainError expected) {
        PlanSnapshot before = plan.snapshot();
        assertRefused(call, expected);
        assertUnchanged(plan, before);
    }

    static void assertPlanAllRefused(
            Plan plan, ThrowingCallable call, DomainError summary, List<? extends DomainError> errors) {
        PlanSnapshot before = plan.snapshot();
        assertAllRefused(call, summary, errors);
        assertUnchanged(plan, before);
    }

    static void assertUnchanged(Plan plan, PlanSnapshot before) {
        assertThat(plan.snapshot()).isEqualTo(before);
        assertThat(plan.pendingEvents()).isEmpty();
        assertThat(plan.pendingRevision()).isEmpty();
    }

    static void assertJobRefused(SolveJob job, ThrowingCallable call, DomainError expected) {
        SolveJobSnapshot before = job.snapshot();
        assertRefused(call, expected);
        assertThat(job.snapshot()).isEqualTo(before);
        assertThat(job.pendingEvents()).isEmpty();
    }
}
