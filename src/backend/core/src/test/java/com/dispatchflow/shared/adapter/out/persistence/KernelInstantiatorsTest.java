package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KernelInstantiatorsTest {

    private static final IsraelDateTime NINE = IsraelDateTime.of(LocalDateTime.of(2027, 7, 13, 9, 0));
    private static final IsraelDateTime FIVE = IsraelDateTime.of(LocalDateTime.of(2027, 7, 13, 17, 0));
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 7, 13));
    private static final double LATITUDE = 32.08;
    private static final double LONGITUDE = 34.78;
    private static final long AN_HOUR = 3_600;

    /** Stored values in the alphabetical order of the attributes' names, as Hibernate hands them. */
    private static ValueAccess stored(@Nullable Object... values) {
        return () -> values;
    }

    @Test
    void GivenAStoredWindow_WhenRebuilt_ThenItIsTheWindowStored() {
        assertThat(new TimeWindowInstantiator().instantiate(stored(FIVE, NINE))).isEqualTo(new TimeWindow(NINE, FIVE));
    }

    @Test
    void GivenAStoredPoint_WhenRebuilt_ThenItIsThePointStored() {
        assertThat(new GeoPointInstantiator().instantiate(stored(LATITUDE, LONGITUDE)))
                .isEqualTo(GeoPoint.of(LATITUDE, LONGITUDE));
    }

    @Test
    void GivenStoredDaySeconds_WhenRebuilt_ThenTheyAreTheSecondsStored() {
        assertThat(new DaySecondsInstantiator().instantiate(stored(DATE, AN_HOUR)))
                .isEqualTo(DaySeconds.of(DATE, AN_HOUR));
    }

    @Test
    void GivenEveryColumnNull_WhenRebuilt_ThenTheValueIsNull() {
        assertThat(new TimeWindowInstantiator().instantiate(stored(null, null))).isNull();
    }

    @Test
    void GivenOnlySomeColumnsNull_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new GeoPointInstantiator().instantiate(stored(LATITUDE, null)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("GeoPoint"));
    }

    @Test
    void GivenAStoredWindowThatEndsBeforeItStarts_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new TimeWindowInstantiator().instantiate(stored(NINE, FIVE)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("TimeWindow"));
    }

    @Test
    void GivenStoredSecondsBeforeTheDay_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new DaySecondsInstantiator().instantiate(stored(DATE, -1L)))
                .isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenAnInstantiator_WhenAskedAboutAValue_ThenItClaimsOnlyItsOwnType() {
        TimeWindowInstantiator instantiator = new TimeWindowInstantiator();
        GeoPoint point = GeoPoint.of(LATITUDE, LONGITUDE);

        assertThat(instantiator.isInstance(new TimeWindow(NINE, FIVE))).isTrue();
        assertThat(instantiator.isSameClass(new TimeWindow(NINE, FIVE))).isTrue();
        assertThat(instantiator.isInstance(point)).isFalse();
        assertThat(instantiator.isSameClass(point)).isFalse();
    }
}
