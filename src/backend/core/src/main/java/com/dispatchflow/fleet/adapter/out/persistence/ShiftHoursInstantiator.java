package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds stored {@link ShiftHours} from their start and end columns (ADR-126). */
public final class ShiftHoursInstantiator extends StoredEmbeddableInstantiator<ShiftHours> {

    // Alphabetical, as Hibernate hands them: end before start.
    private static final int END = 0;
    private static final int START = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public ShiftHoursInstantiator() {
        super(ShiftHours.class);
    }

    @Override
    protected ShiftHours rebuild(ValueAccess values) {
        return new ShiftHours(values.getValue(START, LocalTime.class), values.getValue(END, LocalTime.class));
    }
}
