package com.dispatchflow.planning.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.settings.TravelBucket;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.StoredStateInvalid;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Planning's instantiators: the travel buckets each rebuilds, a null value, and the stored ones refused. */
class PlanningInstantiatorsTest {

    private static final LocalTime SEVEN = LocalTime.of(7, 0);
    private static final LocalTime HALF_PAST_NINE = LocalTime.of(9, 30);
    private static final TravelFactor PEAK = TravelFactor.of(new BigDecimal("1.3"));
    private static final TravelBucket MORNING = new TravelBucket(SEVEN, HALF_PAST_NINE, PEAK);
    private static final TravelBucket EVENING = new TravelBucket(LocalTime.of(15, 30), LocalTime.of(18, 30), PEAK);

    /** Stored values in the alphabetical order of the attributes' names, as Hibernate hands them. */
    private static ValueAccess stored(@Nullable Object... values) {
        return () -> values;
    }

    @Test
    void GivenAStoredBucket_WhenRebuilt_ThenItIsTheBucketStored() {
        assertThat(new TravelBucketInstantiator().instantiate(stored(HALF_PAST_NINE, PEAK, SEVEN)))
                .isEqualTo(MORNING);
    }

    @Test
    void GivenStoredBuckets_WhenRebuilt_ThenTheyAreTheBucketsStored() {
        assertThat(new TravelBucketsInstantiator()
                        .instantiate(stored(EVENING, MORNING, TravelFactor.of(BigDecimal.ONE))))
                .isEqualTo(new TravelBuckets(MORNING, EVENING, TravelFactor.of(BigDecimal.ONE)));
    }

    @Test
    void GivenABucketWithNoColumns_WhenRebuilt_ThenThereIsNone() {
        assertThat(new TravelBucketInstantiator().instantiate(stored(null, null, null)))
                .isNull();
    }

    @Test
    void GivenAStoredBucketThatEndsBeforeItStarts_WhenRebuilt_ThenStoredStateInvalidNamesIt() {
        assertThatThrownBy(() -> new TravelBucketInstantiator().instantiate(stored(SEVEN, PEAK, HALF_PAST_NINE)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("TravelBucket"));
    }

    @Test
    void GivenStoredPeaksThatOverlap_WhenRebuilt_ThenStoredStateInvalidNamesThem() {
        assertThatThrownBy(() -> new TravelBucketsInstantiator().instantiate(stored(MORNING, EVENING, PEAK)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("TravelBuckets"));
    }

    @Test
    void GivenStoredBucketsMissingAPeak_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new TravelBucketsInstantiator().instantiate(stored(null, MORNING, PEAK)))
                .isInstanceOf(StoredStateInvalid.class);
    }
}
