package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D3;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.FIRST;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O6;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.TODAY;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aPlan;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.evaluationOf;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.uuid;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.RouteNotInPlan;
import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.revision.PlanRevision;
import com.dispatchflow.shared.RouteId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A plan's pending revision is the last command's (ADR-059): the repository appends it when it
 * saves the plan, so a command that makes no revision leaves none pending, and a save after it
 * never appends the earlier one twice. A refused command changes nothing, its pending revision
 * included.
 */
class PlanPendingRevisionTest {

    @Test
    void GivenAPlanWhoseLastCommandMadeARevision_WhenTheNextMakesNone_ThenNoRevisionIsPending() {
        Plan plan = revisedOnce();

        plan.recordRouteCheck(evaluationOf(plan).build());

        assertThat(plan.pendingRevision()).isEmpty();
    }

    @Test
    void GivenAPlanWhoseLastCommandMadeARevision_WhenTheNextChangesNothing_ThenNoRevisionIsPending() {
        Plan plan = revisedOnce();

        plan.removeStopOf(O6, FIRST, RemovalCause.ORDER_CANCELLED, LATER);

        assertThat(plan.pendingRevision()).isEmpty();
    }

    @Test
    void GivenAPlanWhoseLastCommandMadeARevision_WhenTheNextIsRefused_ThenRouteNotInPlanAndItsRevisionIsStillPending() {
        Plan plan = revisedOnce();
        PlanRevision pending = plan.pendingRevision().orElseThrow();
        RouteId unknown = RouteId.of(uuid(999));

        assertRefused(
                () -> plan.handOver(
                        unknown, new DriverStanding(D3, true, true), Optional.empty(), false, DISPATCHER, TODAY, LATER),
                new RouteNotInPlan(unknown));

        assertThat(plan.pendingRevision()).contains(pending);
    }

    /** A published plan, its O1 stop then taken off as revision 2, not yet saved. */
    private static Plan revisedOnce() {
        Plan plan = aPlan().published();
        plan.removeStopOf(O1, FIRST, RemovalCause.ORDER_CANCELLED, LATER);
        assertThat(plan.pendingRevision().map(PlanRevision::number)).contains(RevisionNo.of(2));
        return plan;
    }
}
