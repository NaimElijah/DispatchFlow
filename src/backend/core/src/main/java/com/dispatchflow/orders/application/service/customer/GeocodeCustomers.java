package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.orders.application.port.out.GeocoderUnavailable;
import com.dispatchflow.orders.application.port.out.ServiceAreas;
import com.dispatchflow.orders.application.port.out.customer.RoadSnap;
import com.dispatchflow.orders.application.port.out.customer.RoadSnapUnavailable;
import com.dispatchflow.shared.application.Clock;

/**
 * The geocoding job, which works the outbox (FR-12, FR-13, FR-63, FR-64, NFR-135, DL §12): it
 * claims the due requests, at most what is left of the day's {@value
 * GeocodeRequests#DAILY_LOOKUPS} lookups (NFR-135), and calls the {@link Geocoder} for each outside
 * any transaction (DL §6.3). For a point found, it then asks {@link RoadSnap} how far the point
 * lies from the nearest road, still before any transaction (ADR-085), and builds {@code
 * GeocodeResult.Found} from both answers; no match becomes {@code GeocodeResult.NotFound}. Then,
 * one customer per transaction, {@code Customer.recordGeocode} records the answer with the service
 * area and flags the doubtful ones (FR-63), and the request is done. A {@link GeocoderUnavailable}
 * or a {@link RoadSnapUnavailable} leaves the request for the next try, 15 minutes later; after 24
 * hours the job records {@code GeocodeResult.GaveUp}, which leaves the customer flagged for a
 * dispatcher to place its location by hand (FR-64). Past the day's cap the requests wait for the
 * next day, and the dispatcher is told through {@link CustomerQueries#geocodingBacklog} (NFR-135).
 *
 * <p>Built in M7.
 */
public final class GeocodeCustomers {

    private final GeocodeRequests geocodeRequests;
    private final Geocoder geocoder;
    private final RoadSnap roadSnap;
    private final CustomerRepository customers;
    private final ServiceAreas serviceAreas;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param geocodeRequests the geocoding outbox
     * @param geocoder the outside geocoding service
     * @param roadSnap the road network's distance from a found point to the nearest road
     * @param customers where customers are kept
     * @param serviceAreas the company's service area, which a found point is checked against
     * @param clock the time of the job, and the Israel day the cap counts
     * @param auditLog the audit trail
     */
    public GeocodeCustomers(
            GeocodeRequests geocodeRequests,
            Geocoder geocoder,
            RoadSnap roadSnap,
            CustomerRepository customers,
            ServiceAreas serviceAreas,
            Clock clock,
            AuditLog auditLog) {
        this.geocodeRequests = geocodeRequests;
        this.geocoder = geocoder;
        this.roadSnap = roadSnap;
        this.customers = customers;
        this.serviceAreas = serviceAreas;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Works the due requests.
     *
     * <p>Built in M7.
     *
     * @param call the job's server step
     * @return how many requests were answered or given up on
     */
    public int geocodeDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M7");
    }
}
