package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.HeldReportsView;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.application.port.out.OpenAlertsView;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dispatcher's alert list and the held reports waiting for a decision (FR-157, FR-261, FR-335,
 * FR-405, BR-132). Neither takes a date: an alert raised after its plan completed, and a held
 * report, which is a late one, belong to a date already past. Both come from {@link LiveOpsViews}
 * alone; the alert list asks it with today's date from the {@link Clock}. The phone app's Admin and
 * Dispatcher screens read the same list (FR-405).
 *
 * <p>To Be Implemented. Rows: FR-157, FR-261, FR-335, FR-405, BR-132.
 */
@Transactional(readOnly = true)
public class AlertQueries {

    private final LiveOpsViews views;
    private final Clock clock;

    /**
     * Creates the queries.
     *
     * @param views Live Operations' read port
     * @param clock today's date, which tells which plans have completed (BR-102)
     */
    public AlertQueries(LiveOpsViews views, Clock clock) {
        this.views = views;
        this.clock = clock;
    }

    /**
     * Every unacknowledged alert the list shows now: each of a plan not yet completed, and each
     * raised after its plan completed (FR-261).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher or Admin
     * @return the alerts, the newest first; none when no alert is open
     */
    public OpenAlertsView open(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Every report held for a decision, whatever its stop's date (BR-132).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @return the open held reports; none when no report is held
     */
    public HeldReportsView held(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
