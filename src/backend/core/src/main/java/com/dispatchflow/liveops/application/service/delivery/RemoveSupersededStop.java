package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.events.PutBackSuperseded;
import com.dispatchflow.planning.application.api.RemoveSupersededPutBack;

/**
 * Live Operations' own listener on {@code PutBackSuperseded}: a failed stop was corrected to
 * delivered after a put-back, so the put-back's stop is no longer needed, and Planning's {@link
 * RemoveSupersededPutBack} takes it off its plan with the cause {@code CORRECTED_AFTER_PUT_BACK}
 * (FR-404, FR-258, ADR-107, ADR-114). Planning never listens to Live Operations (DL §2.3 rule 2).
 *
 * <p>Delivery is at least once (ADR-064); Planning's call is idempotent, so it records no {@code
 * ProcessedEvents} pair.
 *
 * <p>Built in M8. Rows: FR-404.
 */
public final class RemoveSupersededStop {

    private final RemoveSupersededPutBack removeSupersededPutBack;

    /**
     * Creates the listener's use case.
     *
     * @param removeSupersededPutBack Planning's removal of a superseded put-back's stop
     */
    public RemoveSupersededStop(RemoveSupersededPutBack removeSupersededPutBack) {
        this.removeSupersededPutBack = removeSupersededPutBack;
    }

    /**
     * Asks Planning to take the put-back's stop off the plan of the date the event names.
     *
     * <p>Built in M8.
     *
     * @param event the superseded put-back
     * @param call the listener's server step, which Planning's call carries
     */
    public void on(PutBackSuperseded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
