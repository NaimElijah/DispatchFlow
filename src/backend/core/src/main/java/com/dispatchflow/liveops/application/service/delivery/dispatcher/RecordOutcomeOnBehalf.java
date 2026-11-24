package com.dispatchflow.liveops.application.service.delivery.dispatcher;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.LiveUpdates;
import com.dispatchflow.liveops.application.port.out.PushNotifier;
import com.dispatchflow.liveops.domain.outcome.DispatcherOutcome;
import com.dispatchflow.orders.application.api.StopOrders;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher records a stop's outcome on its driver's behalf, as when the driver's phone can't
 * (FR-73, FR-257, FR-368). It needs no proof (FR-367) and no entitlement, starts no route run, and
 * overtakes every held report, each shown to the dispatcher (BR-134, ADR-107).
 *
 * <p>{@link RecordOutcomeOnBehalfChange#apply} holds the transaction, and reads first: Planning's {@link
 * PublishedRoutes} for the route's stop, order and attempt and its plan with its date, which each
 * alert is stored with, and Customers &amp; Orders' {@link StopOrders} for the door flag. Then it
 * calls {@code Delivery.recordOnBehalf}, creating the delivery on its first fact, with an alert per
 * overtaken held report and the audit record. Its {@code OutcomeRecorded} reconciles the plan (DL
 * §8). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>After the commit, it tells the open screens through {@link LiveUpdates}, and for each alert
 * raised calls {@link PushNotifier#alertStaff}, so a staff phone app in the background learns of it
 * (FR-405, ADR-083).
 *
 * <p>Built in M8; the push through {@link PushNotifier} in M10. Rows: FR-73, FR-257, FR-368, BR-134.
 */
public final class RecordOutcomeOnBehalf {

    private final PushNotifier pushNotifier;
    private final LiveUpdates liveUpdates;
    private final RecordOutcomeOnBehalfChange change;

    /**
     * Creates the use case.
     *
     * @param pushNotifier the staff's phones, told of each alert raised
     * @param liveUpdates the open screens
     * @param change the use case's transaction
     */
    public RecordOutcomeOnBehalf(
            PushNotifier pushNotifier, LiveUpdates liveUpdates, RecordOutcomeOnBehalfChange change) {
        this.pushNotifier = pushNotifier;
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Records the outcome, counting from now.
     *
     * <p>Built in M8; the push in M10.
     *
     * @param actor the dispatcher
     * @param stop the stop
     * @param route the route whose driver it is recorded for
     * @param outcome what the dispatcher records; a failure carries its reason (FR-73)
     * @throws com.dispatchflow.shared.RuleViolation with Live Operations' {@code
     *     NotALeaveAtDoorOrder}, then {@code SameOutcomeAlreadyRecorded} or {@code
     *     OutcomeAlreadyRecorded} (BR-18)
     * @throws NotFound when no plan in force holds the route, or the route doesn't hold the stop
     * @throws StaleVersion when another writer saved the stop's delivery meanwhile; the web adapter
     *     retries the call at once, up to three times, then answers 503 with {@code Retry-After}
     *     (ADR-121)
     */
    public void record(Actor actor, StopId stop, RouteId route, DispatcherOutcome outcome) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
