package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.shared.adapter.out.persistence.StoredEmbeddableInstantiator;
import java.time.Instant;
import org.hibernate.metamodel.spi.ValueAccess;

/**
 * Rebuilds a stored {@link CorrectedTime} through {@code StoredStateInvalid.whileRestoring}, so a
 * device instant its range now refuses is an incident, not a refusal (ADR-127).
 */
public final class CorrectedTimeInstantiator extends StoredEmbeddableInstantiator<CorrectedTime> {

    // Alphabetical, as Hibernate hands them: deviceClockAtSend, deviceTime, receivedAt.
    private static final int DEVICE_CLOCK_AT_SEND = 0;
    private static final int DEVICE_TIME = 1;
    private static final int RECEIVED_AT = 2;

    /** Creates the instantiator, which Hibernate does once. */
    public CorrectedTimeInstantiator() {
        super(CorrectedTime.class);
    }

    @Override
    protected CorrectedTime rebuild(ValueAccess values) {
        return CorrectedTime.of(
                values.getValue(DEVICE_TIME, Instant.class),
                values.getValue(DEVICE_CLOCK_AT_SEND, Instant.class),
                values.getValue(RECEIVED_AT, Instant.class));
    }
}
