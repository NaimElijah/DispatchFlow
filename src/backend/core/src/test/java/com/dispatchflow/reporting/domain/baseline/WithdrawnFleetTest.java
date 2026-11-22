package com.dispatchflow.reporting.domain.baseline;

import static com.dispatchflow.reporting.domain.ReportingFixtures.SHIFT_RAN_OUT_2;
import static com.dispatchflow.reporting.domain.ReportingFixtures.UNAVAILABLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_1;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_2;
import static com.dispatchflow.reporting.domain.ReportingFixtures.VEHICLE_3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How the baseline's solve treats each withdrawn vehicle (FR-271). */
class WithdrawnFleetTest {

    @Test
    void GivenOneVehiclePerCause_WhenSplit_ThenUnavailableAndDriverlessAreLeftOutAndShiftRanOutKeepsItsShift() {
        Withdrawal noDriver3 = new Withdrawal(VEHICLE_3, WithdrawalCause.NO_DRIVER);

        WithdrawnFleet fleet = WithdrawnFleet.of(List.of(UNAVAILABLE_1, SHIFT_RAN_OUT_2, noDriver3));

        assertThat(fleet).isEqualTo(new WithdrawnFleet(List.of(VEHICLE_1, VEHICLE_3), List.of(VEHICLE_2)));
    }

    @Test
    void GivenNoWithdrawal_WhenSplit_ThenNothingIsLeftOutOrGivenItsShift() {
        assertThat(WithdrawnFleet.of(List.of())).isEqualTo(new WithdrawnFleet(List.of(), List.of()));
    }

    @Test
    void GivenAVehicleNamedAgainWithAnotherCause_WhenSplit_ThenItCountsOnceWithItsFirstCause() {
        Withdrawal again = new Withdrawal(VEHICLE_1, WithdrawalCause.SHIFT_RAN_OUT);

        WithdrawnFleet fleet = WithdrawnFleet.of(List.of(UNAVAILABLE_1, again, UNAVAILABLE_1));

        assertThat(fleet).isEqualTo(new WithdrawnFleet(List.of(VEHICLE_1), List.of()));
    }

    @Test
    void GivenAMissingPart_WhenAWithdrawalOrASplitIsMade_ThenItFailsLoudly() {
        assertThatThrownBy(() -> new Withdrawal(VEHICLE_1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Withdrawal(null, WithdrawalCause.NO_DRIVER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WithdrawnFleet.of(null)).isInstanceOf(NullPointerException.class);
    }
}
