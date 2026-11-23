package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.TimeWindow;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds a stored {@link TimeWindow} from its start and end columns (ADR-126). */
public final class TimeWindowInstantiator extends KernelEmbeddableInstantiator<TimeWindow> {

    // Alphabetical, as Hibernate hands them: end before start.
    private static final int END = 0;
    private static final int START = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public TimeWindowInstantiator() {
        super(TimeWindow.class);
    }

    @Override
    TimeWindow rebuild(ValueAccess values) {
        return new TimeWindow(values.getValue(START, IsraelDateTime.class), values.getValue(END, IsraelDateTime.class));
    }
}
