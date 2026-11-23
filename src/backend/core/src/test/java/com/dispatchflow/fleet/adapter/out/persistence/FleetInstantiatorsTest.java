package com.dispatchflow.fleet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Fleet's instantiators: the hours each rebuilds, a null value, and the stored hours refused. */
class FleetInstantiatorsTest {

    private static final LocalTime EIGHT = LocalTime.of(8, 0);
    private static final LocalTime FOUR_PM = LocalTime.of(16, 0);

    /** Stored values in the alphabetical order of the attributes' names, as Hibernate hands them. */
    private static ValueAccess stored(@Nullable Object... values) {
        return () -> values;
    }

    @Test
    void GivenStoredShiftHours_WhenRebuilt_ThenTheyAreTheHoursStored() {
        assertThat(new ShiftHoursInstantiator().instantiate(stored(FOUR_PM, EIGHT)))
                .isEqualTo(ShiftHours.of(EIGHT, FOUR_PM));
    }

    @Test
    void GivenStoredDepotHours_WhenRebuilt_ThenTheyAreTheHoursStored() {
        assertThat(new DepotHoursInstantiator().instantiate(stored(FOUR_PM, EIGHT)))
                .isEqualTo(DepotHours.of(EIGHT, FOUR_PM));
    }

    @Test
    void GivenADepotWithNoHours_WhenItsHoursAreRebuilt_ThenThereAreNone() {
        assertThat(new DepotHoursInstantiator().instantiate(stored(null, null))).isNull();
    }

    @Test
    void GivenStoredShiftHoursThatEndBeforeTheyStart_WhenRebuilt_ThenStoredStateInvalidNamesThem() {
        assertThatThrownBy(() -> new ShiftHoursInstantiator().instantiate(stored(EIGHT, FOUR_PM)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("ShiftHours"));
    }

    @Test
    void GivenStoredDepotHoursThatCloseBeforeTheyOpen_WhenRebuilt_ThenStoredStateInvalidNamesThem() {
        assertThatThrownBy(() -> new DepotHoursInstantiator().instantiate(stored(EIGHT, FOUR_PM)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("DepotHours"));
    }

    @Test
    void GivenStoredDepotHoursMissingTheirClosingTime_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new DepotHoursInstantiator().instantiate(stored(null, EIGHT)))
                .isInstanceOf(StoredStateInvalid.class);
    }
}
