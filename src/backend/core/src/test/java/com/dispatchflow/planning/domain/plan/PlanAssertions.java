package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.shared.DomainAssertions.assertAllRefused;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.shared.DomainError;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * A refused command, or a query, leaves a plan as it was: the same description, no pending event,
 * and the pending revision it had, because every rule is checked before the state changes (DL
 * §3.10). A command that returns having changed nothing also leaves no pending revision, since each
 * command sets it to the one it made. A plan saved in memory keeps the revision its last command
 * made, so these compare with what it had before the call. A refused job keeps the same description
 * and records no event.
 */
final class PlanAssertions {

    private PlanAssertions() {}

    static void assertPlanRefused(Plan plan, ThrowingCallable call, DomainError expected) {
        PlanDescription before = PlanDescription.of(plan);
        Optional<PlanRevision> pending = plan.pendingRevision();
        assertRefused(call, expected);
        assertLeftAsItWas(plan, before, pending);
    }

    static void assertPlanAllRefused(
            Plan plan, ThrowingCallable call, DomainError summary, List<? extends DomainError> errors) {
        PlanDescription before = PlanDescription.of(plan);
        Optional<PlanRevision> pending = plan.pendingRevision();
        assertAllRefused(call, summary, errors);
        assertLeftAsItWas(plan, before, pending);
    }

    /** After a refused call or a query: the plan as it was, with the pending revision it had. */
    static void assertLeftAsItWas(Plan plan, PlanDescription before, Optional<PlanRevision> pending) {
        assertThat(PlanDescription.of(plan)).isEqualTo(before);
        assertThat(plan.pendingEvents()).isEmpty();
        assertThat(plan.pendingRevision()).isEqualTo(pending);
    }

    /** After a command that returned having changed nothing: the plan as it was, and no revision. */
    static void assertUnchanged(Plan plan, PlanDescription before) {
        assertThat(PlanDescription.of(plan)).isEqualTo(before);
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
