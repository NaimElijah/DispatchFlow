package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;

/**
 * The minute job that alerts the dispatcher on stops at risk of missing their window: projected
 * later than the window end less 15 minutes, never a leave-at-door stop (FR-34, FR-253, FR-369,
 * ADR-067). For today's routes in force it builds the {@code PlannedLegs} from Planning's {@link
 * PublishedRoutes}, whose stops carry their windows and door flags, the {@code StopFact}s from the
 * deliveries and the runs' starts, asks {@code ArrivalProjection.isAtRisk}, and raises a <em>stop at
 * risk</em> alert keyed on the revision, so a new revision can alert again (ADR-107). {@link
 * AlertStopsAtRiskChange} holds that transaction.
 *
 * <p>After the commit, it tells the open screens of each alert raised through {@link LiveUpdates},
 * and calls {@link PushNotifier#alertStaff} for each, so a staff phone app in the background learns
 * of it (FR-405, ADR-083).
 *
 * <p>To Be Implemented. It comes with the push through {@link PushNotifier}. Rows: FR-34, FR-253,
 * ADR-067.
 */
public final class AlertStopsAtRisk {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final AlertStopsAtRiskChange change;

    /**
     * Creates the job.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the job's transaction
     */
    public AlertStopsAtRisk(PushNotifier pushNotifier, LiveUpdates liveUpdates, AlertStopsAtRiskChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Raises the alerts now due.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many alerts were raised; one whose key was taken doesn't count
     */
    public int alertDue(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
