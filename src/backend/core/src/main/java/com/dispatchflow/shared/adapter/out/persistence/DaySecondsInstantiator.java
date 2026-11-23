package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds a stored {@link DaySeconds} from its date and seconds columns (ADR-126). */
public final class DaySecondsInstantiator extends KernelEmbeddableInstantiator<DaySeconds> {

    private static final int DATE = 0;
    private static final int SECONDS = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public DaySecondsInstantiator() {
        super(DaySeconds.class);
    }

    @Override
    DaySeconds rebuild(ValueAccess values) {
        return DaySeconds.of(values.getValue(DATE, DeliveryDate.class), values.getValue(SECONDS, Long.class));
    }
}
