package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.TravelBucket;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import org.hibernate.metamodel.spi.ValueAccess;

/**
 * Rebuilds stored {@link TravelBuckets} from their two peaks, which {@link TravelBucketInstantiator}
 * rebuilt first, and their off-peak factor (ADR-126).
 */
public final class TravelBucketsInstantiator extends StoredEmbeddableInstantiator<TravelBuckets> {

    // Alphabetical, as Hibernate hands them: eveningPeak, morningPeak, offPeak.
    private static final int EVENING_PEAK = 0;
    private static final int MORNING_PEAK = 1;
    private static final int OFF_PEAK = 2;

    /** Creates the instantiator, which Hibernate does once. */
    public TravelBucketsInstantiator() {
        super(TravelBuckets.class);
    }

    @Override
    protected TravelBuckets rebuild(ValueAccess values) {
        return new TravelBuckets(
                values.getValue(MORNING_PEAK, TravelBucket.class),
                values.getValue(EVENING_PEAK, TravelBucket.class),
                values.getValue(OFF_PEAK, TravelFactor.class));
    }
}
