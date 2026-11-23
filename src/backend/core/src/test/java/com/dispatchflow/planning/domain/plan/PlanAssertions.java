package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.shared.DomainAssertions.assertAllRefused;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * A refused command leaves a plan as it was: the same snapshot, no pending event and no pending
 * revision, because every rule is checked before the state changes (DL §3.10). A refused job keeps
 * the same description and records no event.
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
        SolveJobDescription before = SolveJobDescription.of(job);
        assertRefused(call, expected);
        assertThat(SolveJobDescription.of(job)).isEqualTo(before);
        assertThat(job.pendingEvents()).isEmpty();
    }
}
