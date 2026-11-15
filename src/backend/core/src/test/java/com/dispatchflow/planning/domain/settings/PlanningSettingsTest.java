package com.dispatchflow.planning.domain.settings;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PlanningError.InvalidTravelBuckets;
import com.dispatchflow.planning.domain.PlanningError.NegativeBuffer;
import com.dispatchflow.planning.domain.PlanningError.NegativeDepartureAllowance;
import com.dispatchflow.planning.domain.PlanningError.NonPositiveFixedVehicleCost;
import com.dispatchflow.planning.domain.PlanningError.NonPositiveTravelFactor;
import com.dispatchflow.planning.domain.SolveLimit;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class PlanningSettingsTest {

    private static final TravelFactor PEAK = TravelFactor.of(new BigDecimal("1.3"));

    @Test
    void GivenNoChanges_WhenTheStartingSettingsAreRead_ThenTheyAreTheRequirementsStartingValues() {
        PlanningSettingsSnapshot settings = PlanningSettings.startingValues().snapshot();

        assertThat(settings.travelBuckets().morningPeak())
                .isEqualTo(new TravelBucket(LocalTime.of(7, 0), LocalTime.of(9, 30), PEAK));
        assertThat(settings.travelBuckets().eveningPeak())
                .isEqualTo(new TravelBucket(LocalTime.of(15, 30), LocalTime.of(18, 30), PEAK));
        assertThat(settings.travelBuckets().offPeak()).isEqualTo(TravelFactor.of(new BigDecimal("1.00")));
        assertThat(settings.buffer()).isEqualTo(Buffer.percent(new BigDecimal("10")));
        assertThat(settings.defaultSolveLimit().duration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(settings.fixedVehicleCost()).isEqualTo(new FixedVehicleCost.Off());
        assertThat(settings.departureAllowance().minutes()).isZero();
    }

    @Test
    void GivenTheSettings_WhenEachIsChanged_ThenTheNewValuesAreKeptAndRestoreRoundTrips() {
        PlanningSettings settings = PlanningSettings.startingValues();
        TravelBuckets buckets = new TravelBuckets(
                new TravelBucket(LocalTime.of(6, 30), LocalTime.of(9, 0), PEAK),
                new TravelBucket(LocalTime.of(9, 0), LocalTime.of(10, 0), TravelFactor.of(new BigDecimal("1.1"))),
                TravelFactor.of(BigDecimal.ONE));

        settings.changeTravelBuckets(buckets);
        settings.changeBuffer(Buffer.percent(BigDecimal.ZERO));
        settings.changeDefaultSolveLimit(SolveLimit.daily(Duration.ofSeconds(60)));
        settings.changeFixedVehicleCost(new FixedVehicleCost.On(Duration.ofMinutes(30)));
        settings.changeDepartureAllowance(DepartureAllowance.ofMinutes(15));

        PlanningSettingsSnapshot changed = settings.snapshot();
        assertThat(changed.travelBuckets()).isEqualTo(buckets);
        assertThat(changed.fixedVehicleCost()).isEqualTo(new FixedVehicleCost.On(Duration.ofMinutes(30)));
        assertThat(changed.departureAllowance().minutes()).isEqualTo(15);
        assertThat(PlanningSettings.restore(changed).snapshot()).isEqualTo(changed);
        assertThat(settings.pendingEvents()).isEmpty();
    }

    @Test
    void GivenABucketThatEndsBeforeItStarts_WhenBuilt_ThenInvalidTravelBuckets() {
        assertRefused(() -> new TravelBucket(LocalTime.of(9, 0), LocalTime.of(9, 0), PEAK), new InvalidTravelBuckets());
    }

    @Test
    void GivenPeaksThatOverlap_WhenBucketsAreBuilt_ThenInvalidTravelBuckets() {
        assertRefused(
                () -> new TravelBuckets(
                        new TravelBucket(LocalTime.of(7, 0), LocalTime.of(10, 0), PEAK),
                        new TravelBucket(LocalTime.of(9, 59), LocalTime.of(11, 0), PEAK),
                        PEAK),
                new InvalidTravelBuckets());
    }

    @Test
    void GivenAFactorOfZero_WhenBuilt_ThenNonPositiveTravelFactor() {
        assertRefused(() -> TravelFactor.of(BigDecimal.ZERO), new NonPositiveTravelFactor());
    }

    @Test
    void GivenANegativeBuffer_WhenBuilt_ThenNegativeBuffer() {
        assertRefused(() -> Buffer.percent(new BigDecimal("-0.01")), new NegativeBuffer());
    }

    @Test
    void GivenAZeroCostPerVehicle_WhenTurnedOn_ThenNonPositiveFixedVehicleCost() {
        assertRefused(() -> new FixedVehicleCost.On(Duration.ZERO), new NonPositiveFixedVehicleCost());
    }

    @Test
    void GivenANegativeAllowance_WhenBuilt_ThenNegativeDepartureAllowance() {
        assertRefused(() -> DepartureAllowance.ofMinutes(-1), new NegativeDepartureAllowance());
    }
}
