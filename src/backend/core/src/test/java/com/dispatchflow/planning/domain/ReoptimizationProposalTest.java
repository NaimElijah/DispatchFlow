package com.dispatchflow.planning.domain;

import static com.dispatchflow.planning.domain.PlanningFixtures.D1;
import static com.dispatchflow.planning.domain.PlanningFixtures.D2;
import static com.dispatchflow.planning.domain.PlanningFixtures.DISPATCHER;
import static com.dispatchflow.planning.domain.PlanningFixtures.LATER;
import static com.dispatchflow.planning.domain.PlanningFixtures.O1;
import static com.dispatchflow.planning.domain.PlanningFixtures.O2;
import static com.dispatchflow.planning.domain.PlanningFixtures.PROPOSAL;
import static com.dispatchflow.planning.domain.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.PlanningFixtures.V2;
import static com.dispatchflow.planning.domain.PlanningFixtures.aJob;
import static com.dispatchflow.planning.domain.PlanningFixtures.dropped;
import static com.dispatchflow.planning.domain.PlanningFixtures.reoptimizationResult;
import static com.dispatchflow.planning.domain.PlanningFixtures.solved;
import static com.dispatchflow.planning.domain.PlanningFixtures.standardSolution;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.PlanningError.ProposalAlreadyDeclined;
import com.dispatchflow.shared.StoredStateInvalid;
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
        ReoptimizationProposalSnapshot twice = proposalOf(List.of(solved(V1, D1, O1), solved(V2, D2, O1)), List.of());

        assertThatThrownBy(() -> ReoptimizationProposal.restore(twice))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.detail()).contains("at most once"));
    }

    @Test
    void GivenAStoredProposalServingAndDroppingAnOrder_WhenRestored_ThenStoredStateInvalid() {
        ReoptimizationProposalSnapshot both =
                proposalOf(List.of(solved(V1, D1, O1)), List.of(dropped(O1, new DropReason.Displaced())));

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
                ValidationReport.of(List.of(), List.of(solved(V1, D1, O1), solved(V1, D1, O2)), List.of()));
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

    private static ReoptimizationProposalSnapshot proposalOf(List<SolvedRoute> routes, List<DroppedOrder> drops) {
        SolveJob job = aJob().reoptimizing().running();
        ValidatedSolution solution = ValidatedSolution.of(ValidationReport.of(List.of(), routes, drops));
        return job.succeed(PROPOSAL, reoptimizationResult(solution), LATER).snapshot();
    }
}
