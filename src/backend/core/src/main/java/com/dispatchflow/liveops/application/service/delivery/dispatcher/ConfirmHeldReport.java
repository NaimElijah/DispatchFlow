package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher confirms a report held for their decision: it becomes the stop's outcome, and every
 * other held report on the stop is refused as BR-90 says (BR-132, BR-135, BR-18, DL §8).
 *
 * <p>{@link ConfirmHeldReportChange#apply} holds the transaction, and reads first: Planning's {@link
 * RouteMembership#driversAt} for the reporting route's driver at the report's corrected time and
 * now, and {@link PublishedRoutes} for its date and plan. Then it calls {@code
 * Delivery.confirmHeld}, with a <em>report refused</em> alert for each other held report its answer
 * names as refused, so each is shown to the dispatcher as BR-90 shows a refused report (BR-135), and
 * the audit record. As {@code RecordOutcomeOnBehalf} does, each alert is stored with its plan's
 * date. The same transaction creates the route's {@code RouteRun} when this was the route's first
 * report and its sender still drives it (ADR-114). Its {@code OutcomeRecorded} reconciles the plan.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p>After the commit, it tells the open screens through {@link LiveUpdates}, and for each alert
 * raised calls {@link PushNotifier#alertStaff}, so a staff phone app in the background learns of it
 * (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: BR-90, BR-132, BR-135.
 */
public final class ConfirmHeldReport {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final ConfirmHeldReportChange change;

    /**
     * Creates the use case.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public ConfirmHeldReport(PushNotifier pushNotifier, LiveUpdates liveUpdates, ConfirmHeldReportChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Confirms the held report, and alerts the dispatcher to each other held report it refuses.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param actor the dispatcher
     * @param stop the stop the report is held on
     * @param report the held report
     * @param expected the delivery's version the dispatcher saw (BR-76)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code ReportNotHeld} when
     *     the report is unknown, confirmed or refused, as when an outcome on the stop's current route
     *     overtook it meanwhile (DL §11)
     * @throws NotFound when nothing is recorded for the stop
     * @throws StaleVersion when the delivery changed since {@code expected}
     */
    public void confirm(Actor actor, StopId stop, HeldReportId report, Version expected) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
