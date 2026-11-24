package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.planning.application.api.PublishedRoutes;

/**
 * The minute job that alerts the dispatcher when a driver on a started route hasn't confirmed a
 * change 10 minutes after its revision took effect (FR-97, FR-157, FR-250, ADR-107, DL §12). For
 * today's runs it asks {@code RouteRun.isConfirmationOverdue} with the revision and time Planning's
 * {@link PublishedRoutes} gives each route, and raises a <em>route change unconfirmed</em> alert,
 * keyed on the revision, so each overdue revision alerts once. {@link AlertUnconfirmedChangesChange}
 * holds that transaction.
 *
 * <p>After the commit, it tells the open screens of each alert raised through {@link LiveUpdates},
 * and calls {@link PushNotifier#alertStaff} for each, so a staff phone app in the background learns
 * of it (FR-405, ADR-083).
 *
 * <p>To Be Implemented. It comes with the push through {@link PushNotifier}. Rows: FR-157, FR-250,
 * ADR-107.
 */
public final class AlertUnconfirmedChanges {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final AlertUnconfirmedChangesChange change;

    /**
     * Creates the job.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the job's transaction
     */
    public AlertUnconfirmedChanges(
            PushNotifier pushNotifier, LiveUpdates liveUpdates, AlertUnconfirmedChangesChange change) {
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
