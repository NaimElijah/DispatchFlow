package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.Clock;

/**
 * Messaging's minute job: it asks Live Operations' {@link ArrivalProjections#projectedOn} for
 * today's stops, because projections change as time passes and raise no event (DL §7.4, §10.3), and
 * calls {@code StopMessages.onArrivalProjected} for each stop that awaits delivery, which may add
 * <em>arriving soon</em> and <em>time changed</em> (FR-352, FR-353, FR-384). Each answer becomes the
 * domain's {@code ArrivalFacts} and {@code ProjectedArrival} (DL §2.3 rule 1); the stops' orders come
 * from Planning's {@link PublishedRoutes}, their phones from {@link RecipientContacts} in one call
 * for the day's orders. Each stop changes in a transaction of its own, with the audit record.
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With no
 * profile there is no sender name, so the run adds no message and answers 0.
 *
 * <p>It makes no outside call and opens no transaction of its own: {@link
 * ProjectArrivalsChange#stopsOn} reads the projections, the routes and the phones in a read-only
 * one, and {@link ProjectArrivalsChange#apply} judges each stop in one of its own (ADR-129).
 *
 * <p>Built in M12. Rows: FR-352, FR-353, FR-384.
 */
public final class ProjectArrivals {

    private final Clock clock;
    private final ProjectArrivalsChange change;

    /**
     * Creates the job.
     *
     * @param clock the server's time and today's date
     * @param change the read of today's stops, and the transaction of each stop's projection
     */
    public ProjectArrivals(Clock clock, ProjectArrivalsChange change) {
        this.clock = clock;
        this.change = change;
    }

    /**
     * Projects today's arrivals and adds the messages now due. A stop another writer saved meanwhile
     * is judged again on the next run.
     *
     * <p>Built in M12.
     *
     * @param call the job's server step
     * @return how many messages it added
     */
    public int projectDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
