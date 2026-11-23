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

import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.PlanningError.ProposalAlreadyDeclined;
import com.dispatchflow.planning.domain.drop.DropReason;
import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.planning.domain.solution.ReoptimizationResult;
import com.dispatchflow.planning.domain.solution.SolvedRoute;
import com.dispatchflow.planning.domain.solution.ValidatedSolution;
import com.dispatchflow.planning.domain.solution.ValidationReport;
import com.dispatchflow.planning.domain.solution.VehicleWithdrawal;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.Attempt;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReoptimizationProposalTest {

    @Test
    void GivenAReadyProposal_WhenDeclined_ThenItIsDeclinedByTheDispatcherWithNoEvent() {
        ReoptimizationProposal proposal = ready();

        proposal.decline(DISPATCHER, LATER);

        assertThat(proposal.state()).isEqualTo(new ReoptimizationProposal.State.Declined(DISPATCHER, LATER));
        assertThat(proposal.pendingEvents()).isEmpty();
    }

    @Test
    void GivenADeclinedProposal_WhenDeclinedAgain_ThenProposalAlreadyDeclined() {
        ReoptimizationProposal proposal = ready();
        proposal.decline(DISPATCHER, LATER);
        ProposalDescription before = ProposalDescription.of(proposal);

        assertRefused(() -> proposal.decline(DISPATCHER, LATER.plusSeconds(1)), new ProposalAlreadyDeclined(PROPOSAL));
        assertThat(ProposalDescription.of(proposal)).isEqualTo(before);
        assertThat(proposal.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAClockThatSteppedBack_WhenAProposalIsDeclinedBeforeItWasCreated_ThenItCountsAsDeclinedWhenCreated() {
        ReoptimizationProposal proposal = ready();
        Instant created = proposal.createdAt();

        proposal.decline(DISPATCHER, created.minusSeconds(1));

        assertThat(proposal.state()).isEqualTo(new ReoptimizationProposal.State.Declined(DISPATCHER, created));
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

    private static ReoptimizationProposal ready() {
        return proposalOf(standardSolution().routes(), standardSolution().dropped());
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

        assertThat(proposal.state()).isEqualTo(new ReoptimizationProposal.State.Ready());
    }

    private static ReoptimizationResult resultOf(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        return reoptimizationResult(ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of())));
    }

    private static ReoptimizationProposal proposalOf(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops, List.of()));
        return job.succeed(PROPOSAL, reoptimizationResult(solution), LATER);
    }
}
