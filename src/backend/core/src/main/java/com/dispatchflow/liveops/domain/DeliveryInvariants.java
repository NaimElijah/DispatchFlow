package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.domain.DeliverySnapshot.HeldReport;
import com.dispatchflow.liveops.domain.DeliverySnapshot.HeldState;
import com.dispatchflow.liveops.domain.DeliverySnapshot.Origin;
import com.dispatchflow.liveops.domain.DeliverySnapshot.OutcomeRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The invariants a {@link Delivery} holds at rest, checked in one place: by {@code restore}, which
 * throws {@code StoredStateInvalid}, and by every command's commit point, which throws {@code
 * AssertionError}, so no command can leave a state that {@code restore} refuses.
 */
final class DeliveryInvariants {

    private DeliveryInvariants() {}

    /**
     * The first invariant the state breaks.
     *
     * @param state the state to check
     * @return what it breaks, or empty when it holds every invariant
     */
    static Optional<String> brokenBy(DeliverySnapshot state) {
        List<OutcomeRecord> outcomes = state.outcomes();
        List<HeldReport> held = state.heldReports();
        for (int i = 0; i < outcomes.size(); i++) {
            boolean correction = outcomes.get(i).origin() instanceof Origin.Correction;
            if (correction != (i > 0)) {
                return Optional.of("the first outcome is the original and every later one a correction (FR-333)");
            }
            if (i > 0 && outcomes.get(i).outcome().isSameAs(outcomes.get(i - 1).outcome())) {
                return Optional.of("a correction changes the outcome it corrects (FR-333)");
            }
        }
        if (!outcomes.isEmpty() && held.stream().anyMatch(HeldReport::isOpen)) {
            return Optional.of("no report stays held once the stop has an outcome (BR-134, BR-18)");
        }
        if (outcomes.isEmpty() && held.stream().anyMatch(report -> report.state() instanceof HeldState.Overtaken)) {
            return Optional.of("a held report is overtaken only by an outcome (BR-134, BR-18)");
        }
        Set<HeldReportId> ids = new HashSet<>();
        for (HeldReport report : held) {
            if (!ids.add(report.id())) {
                return Optional.of("each held report has its own ID");
            }
        }
        Optional<String> confirmation = confirmationBroken(outcomes, held);
        if (confirmation.isPresent()) {
            return confirmation;
        }
        return proofsBroken(outcomes, held);
    }

    private static Optional<String> confirmationBroken(List<OutcomeRecord> outcomes, List<HeldReport> held) {
        List<HeldReport> confirmed = held.stream()
                .filter(report -> report.state() instanceof HeldState.Confirmed)
                .toList();
        Optional<HeldReportId> cited = outcomes.isEmpty()
                ? Optional.empty()
                : outcomes.get(0).origin() instanceof Origin.ConfirmedHeld(HeldReportId report)
                        ? Optional.of(report)
                        : Optional.empty();
        if (confirmed.size() > 1
                || !cited.equals(confirmed.stream().map(HeldReport::id).findFirst())) {
            return Optional.of("a confirmed held report is exactly the original outcome's (BR-132)");
        }
        if (!confirmed.isEmpty()) {
            HeldReport report = confirmed.get(0);
            OutcomeRecord original = outcomes.get(0);
            if (!report.outcome().equals(original.outcome())
                    || !report.driver().equals(original.by())
                    || !report.time().corrected().equals(original.factTime())) {
                return Optional.of("a confirmed outcome is the one its held report states (BR-132)");
            }
        }
        if (!outcomes.isEmpty()
                && outcomes.get(0).origin() instanceof Origin.Reported reported
                && !reported.time().corrected().equals(outcomes.get(0).factTime())) {
            return Optional.of("a reported outcome counts from its report's corrected time (BR-114)");
        }
        return Optional.empty();
    }

    private static Optional<String> proofsBroken(List<OutcomeRecord> outcomes, List<HeldReport> held) {
        boolean reportedBroken = !outcomes.isEmpty()
                && outcomes.get(0).origin() instanceof Origin.Reported
                && !driverProofHolds(outcomes.get(0).outcome());
        boolean heldBroken = held.stream().anyMatch(report -> !driverProofHolds(report.outcome()));
        if (reportedBroken || heldBroken) {
            return Optional.of("a driver's delivered has a proof, and a photo when left at the door (FR-367, FR-368)");
        }
        return Optional.empty();
    }

    private static boolean driverProofHolds(Outcome outcome) {
        if (!(outcome instanceof Outcome.Delivered(Optional<Proof> proof, boolean leftAtDoor))) {
            return true;
        }
        return proof.isPresent() && (!leftAtDoor || proof.get().kind() == ProofKind.PHOTO);
    }
}
