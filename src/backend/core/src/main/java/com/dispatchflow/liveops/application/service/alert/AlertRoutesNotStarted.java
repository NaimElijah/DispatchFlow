package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PlanningSettingsFacts;

/**
 * The minute job that alerts the dispatcher when a route has not started 15 minutes after its
 * driver's shift start plus the departure allowance (FR-101, FR-67, DL §10.3). For today's routes in
 * force with no run, it builds each {@code StartDeadline} from the route driver's shift, read
 * through Fleet's {@link FleetFacts#driverOn}, and Planning's {@link
 * PlanningSettingsFacts#departureAllowance}, and raises a <em>route not started</em> alert once per
 * route. It reads {@code driverOn}, not {@code rosterOn}, which keeps only active drivers: a route
 * published before its driver lost the Driver role still has a deadline (ADR-062). {@link
 * AlertRoutesNotStartedChange} holds that transaction.
 *
 * <p>After the commit, it tells the open screens of each alert raised through {@link LiveUpdates},
 * and calls {@link PushNotifier#alertStaff} for each, so a staff phone app in the background learns
 * of it (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: FR-101.
 */
public final class AlertRoutesNotStarted {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final AlertRoutesNotStartedChange change;

    /**
     * Creates the job.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the job's transaction
     */
    public AlertRoutesNotStarted(
            PushNotifier pushNotifier, LiveUpdates liveUpdates, AlertRoutesNotStartedChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Raises the alerts now due.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param call the job's server step
     * @return how many alerts were raised; one whose key was taken doesn't count
     */
    public int alertDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
