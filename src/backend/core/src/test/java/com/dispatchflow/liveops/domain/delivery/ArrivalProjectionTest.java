package com.dispatchflow.liveops.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.liveops.domain.LiveOpsFixtures;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Projected arrivals (DL §10.4, ADR-067, FR-254, FR-383) on Monday 1 March 2027, UTC+2: planned
 * departure 08:00 local (06:00Z). Every expected instant is written out.
 */
class ArrivalProjectionTest {

    static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 3, 1));
    static final DaySeconds DEPARTURE = DaySeconds.of(DATE, Duration.ofHours(8).toSeconds());

    private static final StopId S1 = StopId.of(LiveOpsFixtures.uuid(101));
    private static final StopId S2 = StopId.of(LiveOpsFixtures.uuid(102));
    private static final StopId S3 = StopId.of(LiveOpsFixtures.uuid(103));

    /** S1: 30 min out, 10 min there, window 09:00–12:00; S2: 20 and 10, 11:00–13:00; S3: 15 and 5, 08:00–18:00. */
    private static final PlannedLegs ROUTE = new PlannedLegs(
            DEPARTURE,
            List.of(leg(S1, 30, 10, 9, 12, false), leg(S2, 20, 10, 11, 13, false), leg(S3, 15, 5, 8, 18, false)));

    static PlannedStopLeg leg(StopId stop, int legMinutes, int serviceMinutes, int opens, int closes, boolean door) {
        return new PlannedStopLeg(
                stop,
                Duration.ofMinutes(legMinutes),
                Duration.ofMinutes(serviceMinutes),
                TimeWindow.on(DATE, LocalTime.of(opens, 0), LocalTime.of(closes, 0)),
                door);
    }

    private static Instant at(String utc) {
        return Instant.parse("2027-03-01T" + utc + "Z");
    }

    private static StopFact outcome(StopId stop, String utc) {
        return new StopFact(stop, Optional.empty(), Optional.of(at(utc)));
    }

    @Test
    void GivenNoEventAndNowBeforeTheDeparture_WhenProjected_ThenThePlannedTimesStandAndWindowsAbsorbWaiting() {
        ArrivalProjection projection = ArrivalProjection.of(ROUTE, List.of(), Optional.empty(), at("05:00:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("06:30:00"));
        assertThat(projection.arrivalOf(S2)).contains(at("07:30:00"));
        assertThat(projection.arrivalOf(S3)).contains(at("09:25:00"));
    }

    @Test
    void GivenNoEventAndNowPastTheFirstArrival_WhenProjected_ThenNoArrivalFallsBeforeNow() {
        ArrivalProjection projection = ArrivalProjection.of(ROUTE, List.of(), Optional.empty(), at("07:00:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("07:00:00"));
        assertThat(projection.arrivalOf(S2)).contains(at("07:30:00"));
    }

    @Test
    void GivenALateStart_WhenProjected_ThenTheDelayCarriesFromTheStart() {
        ArrivalProjection projection =
                ArrivalProjection.of(ROUTE, List.of(), Optional.of(at("06:40:00")), at("06:45:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("07:10:00"));
        assertThat(projection.arrivalOf(S2)).contains(at("07:40:00"));
    }

    @Test
    void GivenAStartBeforeThePlannedDeparture_WhenProjected_ThenTheVehicleLeavesAtTheStart() {
        ArrivalProjection projection =
                ArrivalProjection.of(ROUTE, List.of(), Optional.of(at("05:50:00")), at("05:55:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("06:20:00"));
        assertThat(projection.arrivalOf(S2)).contains(at("07:30:00"));
    }

    @Test
    void GivenAnOutcomeAtTheStartsInstant_WhenProjected_ThenTheStopAnchorsTheChain() {
        ArrivalProjection projection = ArrivalProjection.of(
                ROUTE, List.of(outcome(S1, "06:40:00")), Optional.of(at("06:40:00")), at("06:45:00"));

        assertThat(projection.arrivalOf(S1)).isEmpty();
        assertThat(projection.arrivalOf(S2)).contains(at("07:00:00"));
    }

    @Test
    void GivenTheVehicleStillAtAStop_WhenProjected_ThenItLeavesAtTheLaterOfArrivalPlusServiceAndNow() {
        StopFact arrived = new StopFact(S1, Optional.of(at("07:05:00")), Optional.empty());

        ArrivalProjection projection =
                ArrivalProjection.of(ROUTE, List.of(arrived), Optional.of(at("06:00:00")), at("07:20:00"));

        assertThat(projection.arrivalOf(S1)).isEmpty();
        assertThat(projection.arrivalOf(S2)).contains(at("07:40:00"));
    }

    @Test
    void GivenAnOutcome_WhenProjected_ThenTheVehicleLeavesAtTheOutcomesTime() {
        ArrivalProjection projection = ArrivalProjection.of(
                ROUTE, List.of(outcome(S1, "07:12:00")), Optional.of(at("06:00:00")), at("07:15:00"));

        assertThat(projection.arrivalOf(S2)).contains(at("07:32:00"));
    }

    @Test
    void
            GivenStopsReportedOutOfOrder_WhenProjected_ThenASkippedStopHasItsPlannedArrivalAndAPassedOneIsPassedOnItsLeg() {
        ArrivalProjection skipped = ArrivalProjection.of(
                ROUTE, List.of(outcome(S2, "07:00:00")), Optional.of(at("06:00:00")), at("07:00:00"));
        ArrivalProjection passed = ArrivalProjection.of(
                ROUTE,
                List.of(outcome(S1, "07:10:00"), outcome(S2, "06:50:00")),
                Optional.of(at("06:00:00")),
                at("07:10:00"));

        assertThat(skipped.arrivalOf(S1)).contains(at("07:00:00"));
        assertThat(skipped.arrivalOf(S3)).contains(at("07:15:00"));
        assertThat(passed.arrivalOf(S2)).isEmpty();
        assertThat(passed.arrivalOf(S3)).contains(at("07:45:00"));
    }

    @Test
    void
            GivenARouteStartedByAReportOnItsLastStop_WhenProjected_ThenEachSkippedStopHasItsPlannedArrivalNoEarlierThanNow() {
        ArrivalProjection projection = ArrivalProjection.of(
                ROUTE, List.of(outcome(S3, "06:50:00")), Optional.of(at("06:00:00")), at("07:00:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("07:00:00"));
        assertThat(projection.arrivalOf(S2)).contains(at("07:30:00"));
        assertThat(projection.arrivalOf(S3)).isEmpty();
    }

    @Test
    void GivenASkippedStopWhoseArrivalIsNowPastItsRiskMargin_WhenJudged_ThenItIsNotAtRiskOnThatBasisAlone() {
        ArrivalProjection projection = ArrivalProjection.of(
                ROUTE, List.of(outcome(S2, "09:45:00")), Optional.of(at("06:00:00")), at("09:50:00"));

        assertThat(projection.arrivalOf(S1)).contains(at("09:50:00"));
        assertThat(projection.isAtRisk(S1)).isFalse();
    }

    @Test
    void GivenAProjectionAtTheRiskMargin_WhenJudged_ThenOnlyOneLaterThanItIsAtRisk() {
        PlannedLegs single = new PlannedLegs(DEPARTURE, List.of(leg(S1, 0, 10, 9, 12, false)));

        ArrivalProjection atMargin = ArrivalProjection.of(single, List.of(), Optional.empty(), at("09:45:00"));
        ArrivalProjection past = ArrivalProjection.of(single, List.of(), Optional.empty(), at("09:45:01"));

        assertThat(atMargin.isAtRisk(S1)).isFalse();
        assertThat(past.isAtRisk(S1)).isTrue();
    }

    @Test
    void GivenADoorStop_WhenProjectedLateOrEarly_ThenItIsNeverAtRiskAndNeverWaits() {
        PlannedLegs door =
                new PlannedLegs(DEPARTURE, List.of(leg(S1, 30, 10, 11, 12, true), leg(S2, 20, 10, 8, 18, false)));

        ArrivalProjection early = ArrivalProjection.of(door, List.of(), Optional.empty(), at("05:00:00"));
        ArrivalProjection late = ArrivalProjection.of(door, List.of(), Optional.empty(), at("11:00:00"));

        assertThat(early.arrivalOf(S2)).contains(at("07:00:00"));
        assertThat(late.isAtRisk(S1)).isFalse();
        assertThat(late.isAtRisk(LiveOpsFixtures.STOP)).isFalse();
    }
}
