package com.dispatchflow.liveops.domain;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.LATE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.NOW;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.ONLINE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.SIGNATURE_FILE;
import static com.dispatchflow.liveops.domain.LiveOpsFixtures.T_0800;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.liveops.domain.LiveOpsError.CorrectionReasonRequired;
import com.dispatchflow.liveops.domain.LiveOpsError.FailureReasonRequired;
import com.dispatchflow.liveops.domain.LiveOpsError.InvalidRevision;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.SharedKernelError.TextTooLong;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Live Operations' values check themselves (DL §3.3). */
class LiveOpsValuesTest {

    @Test
    void GivenADeviceClockAnHourSlow_WhenCorrected_ThenTheOffsetMovesTheDeviceTime() {
        CorrectedTime time = CorrectedTime.of(
                Instant.parse("2027-03-01T07:00:00Z"),
                Instant.parse("2027-03-01T08:00:00Z"),
                Instant.parse("2027-03-01T09:00:00Z"));

        assertThat(time.corrected()).isEqualTo(Instant.parse("2027-03-01T08:00:00Z"));
    }

    @Test
    void GivenADeviceTimeAfterItsSend_WhenCorrected_ThenItIsCutToTheReceipt() {
        CorrectedTime time = CorrectedTime.of(
                Instant.parse("2027-03-01T08:30:00Z"),
                Instant.parse("2027-03-01T08:00:00Z"),
                Instant.parse("2027-03-01T08:00:00Z"));

        assertThat(time.corrected()).isEqualTo(Instant.parse("2027-03-01T08:00:00Z"));
    }

    @Test
    void GivenALateReport_WhenItsWindowIsAsked_ThenItRunsFromTwoMinutesAfterToTwoMinutesBefore() {
        assertThat(LATE.contactWindow())
                .contains(new ContactWindow(
                        Instant.parse("2027-03-01T08:02:00Z"), Instant.parse("2027-03-01T09:58:00Z")));
    }

    @ParameterizedTest(name = "received {0}: window {1}")
    @CsvSource({"2027-03-01T08:03:59Z, false", "2027-03-01T08:04:00Z, true"})
    void GivenAReportReceivedAroundFourMinutesLater_WhenItsWindowIsAsked_ThenItOpensAtFourMinutes(
            String received, boolean window) {
        Instant at = Instant.parse(received);

        assertThat(CorrectedTime.of(T_0800, at, at).contactWindow().isPresent()).isEqualTo(window);
        assertThat(ONLINE.contactWindow()).isEmpty();
    }

    @Test
    void GivenAWindowEndingBeforeItStarts_WhenBuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new ContactWindow(NOW, T_0800)).isInstanceOf(StoredStateInvalid.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void GivenARevisionBelowOne_WhenBuilt_ThenInvalidRevision(int number) {
        assertRefused(() -> RevisionNo.of(number), new InvalidRevision(number));
    }

    @Test
    void GivenAnAttemptBelowOne_WhenBuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> Attempt.of(0)).isInstanceOf(StoredStateInvalid.class);
        assertThat(Attempt.of(1).number()).isEqualTo(1);
    }

    @Test
    void GivenOtherWithBlankText_WhenBuilt_ThenFailureReasonRequired() {
        assertRefused(() -> FailureReason.other("   "), new FailureReasonRequired());
    }

    @Test
    void GivenOtherWithNoText_WhenBuiltAsAFixedReason_ThenFailureReasonRequired() {
        assertRefused(() -> FailureReason.of(FailureReasonKind.OTHER), new FailureReasonRequired());
    }

    @Test
    void GivenOtherWithText_WhenBuilt_ThenItKeepsTheNormalizedTextAndPrintsItRedacted() {
        FailureReason reason = FailureReason.other("  gate locked  ");

        assertThat(reason.kind()).isEqualTo(FailureReasonKind.OTHER);
        assertThat(reason.text()).contains("gate locked");
        assertThat(reason.toString()).isEqualTo("FailureReason[OTHER, redacted]");
    }

    @Test
    void GivenAFixedReasonWithAStoredText_WhenRestored_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> FailureReason.ofStored(FailureReasonKind.DAMAGED, Optional.of("x")))
                .isInstanceOf(StoredStateInvalid.class);
        assertThat(FailureReason.ofStored(FailureReasonKind.OTHER, Optional.of("x")))
                .isEqualTo(FailureReason.other("x"));
        assertThat(FailureReason.ofStored(FailureReasonKind.OTHER, Optional.empty()))
                .isEqualTo(FailureReason.other("x").erased());
    }

    @Test
    void GivenAnOtherReason_WhenErased_ThenItKeepsItsKindAndLosesItsText() {
        FailureReason erased = FailureReason.other("gate locked").erased();

        assertThat(erased.kind()).isEqualTo(FailureReasonKind.OTHER);
        assertThat(erased.text()).isEmpty();
        assertThat(erased.isErased()).isTrue();
        assertThat(FailureReason.of(FailureReasonKind.DAMAGED).erased())
                .isEqualTo(FailureReason.of(FailureReasonKind.DAMAGED));
    }

    @Test
    void GivenAReasonOverTheNoteCap_WhenBuilt_ThenTextTooLong() {
        assertRefused(() -> FailureReason.other("a".repeat(501)), new TextTooLong(TextKind.NOTE, 500));
    }

    @Test
    void GivenABlankCorrectionReason_WhenBuilt_ThenCorrectionReasonRequired() {
        assertRefused(() -> CorrectionReason.of("   "), new CorrectionReasonRequired());
        assertThat(CorrectionReason.of(" wrong stop ").text()).contains("wrong stop");
        assertThat(CorrectionReason.of("wrong stop").erased().isErased()).isTrue();
        assertThat(CorrectionReason.ofStored(Optional.empty()))
                .isEqualTo(CorrectionReason.of("x").erased());
        assertThat(CorrectionReason.of("wrong stop").toString()).isEqualTo("CorrectionReason[redacted]");
    }

    @Test
    void GivenABlankProofNote_WhenRead_ThenThereIsNoNote() {
        assertThat(ProofNote.of("  ")).isEmpty();
        assertThat(ProofNote.of(" porch ")).contains(new ProofNote("porch"));
    }

    @Test
    void GivenANoteWithoutItsFile_WhenAProofIsBuilt_ThenStoredStateInvalid() {
        Optional<ProofNote> note = ProofNote.of("porch");

        assertThatThrownBy(() -> new Proof(ProofKind.PHOTO, Optional.empty(), note))
                .isInstanceOf(StoredStateInvalid.class);
        assertThat(Proof.of(ProofKind.PHOTO, SIGNATURE_FILE, note).erased().isErased())
                .isTrue();
    }

    @Test
    void GivenAShiftStartAndAnAllowance_WhenTheStartDeadlineIsBuilt_ThenItIsFifteenMinutesPastBoth() {
        StartDeadline deadline =
                StartDeadline.of(IsraelDateTime.of(LocalDateTime.of(2027, 3, 1, 8, 0)), Duration.ofMinutes(20));

        assertThat(deadline.at()).isEqualTo(Instant.parse("2027-03-01T06:35:00Z"));
        assertThat(deadline.isPassed(Instant.parse("2027-03-01T06:34:59Z"))).isFalse();
        assertThat(deadline.isPassed(Instant.parse("2027-03-01T06:35:00Z"))).isTrue();
    }

    @Test
    void GivenARevision_WhenItsConfirmationDeadlineIsBuilt_ThenItIsTenMinutesLater() {
        ConfirmationDeadline deadline = ConfirmationDeadline.after(T_0800);

        assertThat(deadline.at()).isEqualTo(Instant.parse("2027-03-01T08:10:00Z"));
    }

    @Test
    void GivenTheRetentionPeriods_WhenCutOffsAreAsked_ThenTheyCountDaysInIsraelTime() {
        Instant now = Instant.parse("2027-04-01T07:00:00Z");

        assertThat(RetentionPeriods.positionCutoff(now)).isEqualTo(Instant.parse("2027-03-25T08:00:00Z"));
        assertThat(RetentionPeriods.proofCutoff(Instant.parse("2027-05-30T07:00:00Z")))
                .isEqualTo(Instant.parse("2027-03-01T08:00:00Z"));
        assertThat(RetentionPeriods.reasonCutoff(Instant.parse("2027-05-30T07:00:00Z")))
                .isEqualTo(Instant.parse("2027-03-01T08:00:00Z"));
    }

    @Test
    void GivenARouteWithOneStopTwice_WhenItsLegsAreBuilt_ThenStoredStateInvalid() {
        PlannedStopLeg leg = ArrivalProjectionTest.leg(LiveOpsFixtures.STOP, 10, 5, 9, 12, false);

        assertThatThrownBy(() -> new PlannedLegs(ArrivalProjectionTest.DEPARTURE, java.util.List.of(leg, leg)))
                .isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> ArrivalProjectionTest.leg(LiveOpsFixtures.STOP, -1, 5, 9, 12, false))
                .isInstanceOf(StoredStateInvalid.class);
    }
}
