package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.DailyWindow;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;

/** Rebuilds a stored {@link DailyWindow} from its opening and closing columns (ADR-126). */
public final class DailyWindowInstantiator extends StoredEmbeddableInstantiator<DailyWindow> {

    // Alphabetical, as Hibernate hands them: end before start.
    private static final int END = 0;
    private static final int START = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public DailyWindowInstantiator() {
        super(DailyWindow.class);
    }

    @Override
    protected DailyWindow rebuild(ValueAccess values) {
        return new DailyWindow(values.getValue(START, LocalTime.class), values.getValue(END, LocalTime.class));
    }
}
