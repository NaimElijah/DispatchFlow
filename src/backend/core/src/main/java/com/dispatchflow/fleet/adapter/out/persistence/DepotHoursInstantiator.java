package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.register.DepotHours;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds stored {@link DepotHours} from their opening and closing columns (ADR-126). */
public final class DepotHoursInstantiator extends StoredEmbeddableInstantiator<DepotHours> {

    // Alphabetical, as Hibernate hands them: closes before opens.
    private static final int CLOSES = 0;
    private static final int OPENS = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public DepotHoursInstantiator() {
        super(DepotHours.class);
    }

    @Override
    protected DepotHours rebuild(ValueAccess values) {
        return new DepotHours(values.getValue(OPENS, LocalTime.class), values.getValue(CLOSES, LocalTime.class));
    }
}
