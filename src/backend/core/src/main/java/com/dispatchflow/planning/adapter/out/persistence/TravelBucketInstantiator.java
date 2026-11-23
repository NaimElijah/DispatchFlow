package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.TravelBucket;
import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds a stored {@link TravelBucket} from its start, end and factor columns (ADR-126). */
public final class TravelBucketInstantiator extends StoredEmbeddableInstantiator<TravelBucket> {

    // Alphabetical, as Hibernate hands them: end, factor, start.
    private static final int END = 0;
    private static final int FACTOR = 1;
    private static final int START = 2;

    /** Creates the instantiator, which Hibernate does once. */
    public TravelBucketInstantiator() {
        super(TravelBucket.class);
    }

    @Override
    protected TravelBucket rebuild(ValueAccess values) {
        return new TravelBucket(
                values.getValue(START, LocalTime.class),
                values.getValue(END, LocalTime.class),
                values.getValue(FACTOR, TravelFactor.class));
    }
}
