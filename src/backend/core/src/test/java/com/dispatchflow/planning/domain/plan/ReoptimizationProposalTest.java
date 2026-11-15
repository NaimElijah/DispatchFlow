package com.dispatchflow.planning.domain.plan;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.standardSolution;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.ProposalAlreadyDeclined;
import com.dispatchflow.planning.domain.solution.DropReason;
import com.dispatchflow.planning.domain.solution.DroppedOrder;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReoptimizationProposalTest {

    @Test
    void GivenAReadyProposal_WhenDeclined_ThenItIsDeclinedByTheDispatcherWithNoEvent() {
        ReoptimizationProposal proposal = ready();

        proposal.decline(DISPATCHER, LATER);

        assertThat(proposal.snapshot().state()).isEqualTo(new ReoptimizationProposal.State.Declined(DISPATCHER, LATER));
        assertThat(proposal.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADeclinedProposal_WhenDeclinedAgain_ThenProposalAlreadyDeclined() {
        ReoptimizationProposal proposal = ready();
        proposal.decline(DISPATCHER, LATER);
        ReoptimizationProposalSnapshot before = proposal.snapshot();

        assertRefused(() -> proposal.decline(DISPATCHER, LATER.plusSeconds(1)), new ProposalAlreadyDeclined(PROPOSAL));
        assertThat(proposal.snapshot()).isEqualTo(before);
        assertThat(proposal.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAClockThatSteppedBack_WhenAProposalIsDeclinedBeforeItWasCreated_ThenItCountsAsDeclinedWhenCreated() {
        ReoptimizationProposal proposal = ready();
        Instant created = proposal.snapshot().createdAt();

        proposal.decline(DISPATCHER, created.minusSeconds(1));

        assertThat(proposal.snapshot().state())
                .isEqualTo(new ReoptimizationProposal.State.Declined(DISPATCHER, created));
        assertThat(ReoptimizationProposal.restore(proposal.snapshot()).snapshot())
                .isEqualTo(proposal.snapshot());
    }

    @Test
    void GivenAReadyAndADeclinedProposal_WhenTheirSnapshotsAreRestored_ThenTheyRoundTrip() {
        ReoptimizationProposal proposal = ready();
        assertThat(ReoptimizationProposal.restore(proposal.snapshot()).snapshot())
                .isEqualTo(proposal.snapshot());

        proposal.decline(DISPATCHER, LATER);

        assertThat(ReoptimizationProposal.restore(proposal.snapshot()).snapshot())
                .isEqualTo(proposal.snapshot());
    }

    @Test
    void GivenAStoredProposalServingAnOrderTwice_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot twice = stored(List.of(solved(V1, D1, O1), solved(V2, D2, O1)), List.of());

        assertThatThrownBy(() -> ReoptimizationProposal.restore(twice))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most once"));
    }

    @Test
    void GivenAStoredProposalServingAndDroppingAnOrder_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot both =
                stored(List.of(solved(V1, D1, O1)), List.of(dropped(O1, new DropReason.Displaced())));

        assertThatThrownBy(() -> ReoptimizationProposal.restore(both))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most once"));
    }

    @Test
    void GivenAStoredProposalGivingAVehicleTwoRoutes_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot s = ready().snapshot();
        ReoptimizationProposalSnapshot twice = new ReoptimizationProposalSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.job(),
                s.basis(),
                List.of(solved(V1, D1, O1), solved(V1, D2, O2)),
                List.of(),
                List.of(),
                s.withdrawn(),
                s.objective(),
                s.lateStops(),
                s.withoutUsablePosition(),
                s.driverChanges(),
                s.needsGoodsHandOver(),
                s.createdAt(),
                s.state());

        assertThatThrownBy(() -> ReoptimizationProposal.restore(twice))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most one route"));
    }

    @Test
    void GivenAStoredProposalDeclinedBeforeItWasCreated_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot s = ready().snapshot();
        ReoptimizationProposalSnapshot early = new ReoptimizationProposalSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.job(),
                s.basis(),
                s.routes(),
                s.dropped(),
                s.excluded(),
                s.withdrawn(),
                s.objective(),
                s.lateStops(),
                s.withoutUsablePosition(),
                s.driverChanges(),
                s.needsGoodsHandOver(),
                s.createdAt(),
                new ReoptimizationProposal.State.Declined(
                        DISPATCHER, s.createdAt().minusSeconds(1)));

        assertThatThrownBy(() -> ReoptimizationProposal.restore(early))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("declined no earlier"));
    }

    @Test
    void GivenASolutionGivingAWithdrawnVehicleTwoRoutes_WhenItBecomesAProposal_ThenVehicleRoutedTwice() {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution twice = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V1, D1, O2)), List.of(), List.of()));
        ReoptimizationResult result =
                reoptimizationResult(twice, new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE));

        PlanAssertions.assertJobRefused(
                job, () -> job.succeed(PROPOSAL, result, LATER), new PlanningError.VehicleRoutedTwice(V1));
    }

    @Test
    void GivenAStoredProposalGivingAWithdrawnVehicleTwoRoutes_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot s = ready().snapshot();
        ReoptimizationProposalSnapshot twice = new ReoptimizationProposalSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.job(),
                s.basis(),
                List.of(solved(V1, D1, O1), solved(V1, D2, O2)),
                List.of(),
                List.of(),
                List.of(new VehicleWithdrawal(V1, WithdrawalCause.NO_DRIVER)),
                s.objective(),
                s.lateStops(),
                s.withoutUsablePosition(),
                s.driverChanges(),
                s.needsGoodsHandOver(),
                s.createdAt(),
                s.state());

        assertThatThrownBy(() -> ReoptimizationProposal.restore(twice))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most one route"));
    }

    private static ReoptimizationProposal ready() {
        return ReoptimizationProposal.restore(
                proposalOf(standardSolution().routes(), standardSolution().dropped()));
    }

    @Test
    void GivenAStoredProposalPuttingADriverOnTwoRoutes_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot twice = stored(List.of(solved(V1, D1, O1), solved(V2, D1, O2)), List.of());

        assertThatThrownBy(() -> ReoptimizationProposal.restore(twice))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most one route"));
    }

    @Test
    void GivenASolutionServingAnOrderTwice_WhenItBecomesAProposal_ThenOrderAlreadyInPlan() {
        SolveJob job = aJob().reoptimizing().running();
        ReoptimizationResult result = resultOf(List.of(solved(V1, D1, O1), solved(V2, D2, O1)), List.of());

        PlanAssertions.assertJobRefused(
                job,
                () -> job.succeed(PROPOSAL, result, LATER),
                new PlanningError.OrderAlreadyInPlan(O1, Attempt.FIRST));
    }

    @Test
    void GivenASolutionServingAndDroppingAnOrder_WhenItBecomesAProposal_ThenOrderAlreadyInPlan() {
        SolveJob job = aJob().reoptimizing().running();
        ReoptimizationResult result =
                resultOf(List.of(solved(V1, D1, O1)), List.of(dropped(O1, new DropReason.Displaced())));

        PlanAssertions.assertJobRefused(
                job,
                () -> job.succeed(PROPOSAL, result, LATER),
                new PlanningError.OrderAlreadyInPlan(O1, Attempt.FIRST));
    }

    @Test
    void GivenASolutionPuttingADriverOnTwoRoutes_WhenItBecomesAProposal_ThenDriverRoutedTwice() {
        SolveJob job = aJob().reoptimizing().running();
        ReoptimizationResult result = resultOf(List.of(solved(V1, D1, O1), solved(V2, D1, O2)), List.of());

        PlanAssertions.assertJobRefused(
                job, () -> job.succeed(PROPOSAL, result, LATER), new PlanningError.DriverRoutedTwice(D1));
    }

    @Test
    void GivenADriverOnAWithdrawnRouteAndOnANewOne_WhenItBecomesAProposal_ThenItIsReady() {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V2, D1, O2)), List.of(), List.of()));

        ReoptimizationProposal proposal = job.succeed(
                PROPOSAL,
                reoptimizationResult(solution, new VehicleWithdrawal(V1, WithdrawalCause.VEHICLE_UNAVAILABLE)),
                LATER);

        assertThat(proposal.snapshot().state()).isEqualTo(new ReoptimizationProposal.State.Ready());
        assertThat(ReoptimizationProposal.restore(proposal.snapshot()).snapshot())
                .isEqualTo(proposal.snapshot());
    }

    private static ReoptimizationResult resultOf(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        return reoptimizationResult(ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of())));
    }

    /** A stored proposal with these routes and drops, as persistence would hand it to restore. */
    private static ReoptimizationProposalSnapshot stored(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        ReoptimizationProposalSnapshot s = ready().snapshot();
        return new ReoptimizationProposalSnapshot(
                s.id(),
                s.version(),
                s.plan(),
                s.job(),
                s.basis(),
                routes,
                drops,
                List.of(),
                s.withdrawn(),
                s.objective(),
                s.lateStops(),
                s.withoutUsablePosition(),
                s.driverChanges(),
                s.needsGoodsHandOver(),
                s.createdAt(),
                s.state());
    }

    private static ReoptimizationProposalSnapshot proposalOf(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution), LATER).snapshot();
    }
}
