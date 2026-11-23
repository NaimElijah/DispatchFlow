package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.companyprofile.DefaultWindow;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.LocalTime;
import org.hibernate.metamodel.spi.ValueAccess;

/**
 * Rebuilds a stored {@link DefaultWindow} through {@code StoredStateInvalid.whileRestoring}, so a
 * window that no longer opens before it closes is an incident, not a refusal (ADR-127).
 */
public final class DefaultWindowInstantiator extends StoredEmbeddableInstantiator<DefaultWindow> {

    // Alphabetical, as Hibernate hands them: closes before opens.
    private static final int CLOSES = 0;
    private static final int OPENS = 1;

    /** Creates the instantiator, which Hibernate does once. */
    public DefaultWindowInstantiator() {
        super(DefaultWindow.class);
    }

    @Override
    protected DefaultWindow rebuild(ValueAccess values) {
        return new DefaultWindow(values.getValue(OPENS, LocalTime.class), values.getValue(CLOSES, LocalTime.class));
    }
}
