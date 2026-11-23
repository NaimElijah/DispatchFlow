package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.GeoPoint;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds a stored {@link GeoPoint} from its latitude and longitude columns (ADR-126). */
public final class GeoPointInstantiator extends KernelEmbeddableInstantiator<GeoPoint> {

    private static final int LATITUDE = 0;
    private static final int LONGITUDE = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public GeoPointInstantiator() {
        super(GeoPoint.class);
    }

    @Override
    GeoPoint rebuild(ValueAccess values) {
        return GeoPoint.of(values.getValue(LATITUDE, Double.class), values.getValue(LONGITUDE, Double.class));
    }
}
