package com.dispatchflow.orders.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.application.port.out.Geocoder;
import com.dispatchflow.orders.application.port.out.GeocoderUnavailable;
import com.dispatchflow.orders.application.port.out.ServiceAreas;
import com.dispatchflow.shared.application.Clock;

/**
 * The geocoding job, which works the outbox (FR-12, FR-13, FR-63, FR-64, NFR-135, DL §12): it
 * claims the due requests, at most what is left of the day's 1,000 lookups (NFR-135), and calls the
 * {@link Geocoder} for each outside any transaction (DL §6.3). Then, one customer per transaction,
 * {@code Customer.recordGeocode} records the answer with the service area and flags the doubtful
 * ones (FR-63), and the request is done. A {@link GeocoderUnavailable} leaves the request for the
 * next try, 15 minutes later; after 24 hours the job records {@code GeocodeResult.GaveUp}, which
 * leaves the customer flagged for a hand-placed pin (FR-64). Past the day's cap the requests wait
 * for the next day, and the dispatcher is told (NFR-135).
 *
 * <p>Built in M7.
 */
public final class GeocodeCustomers {

    private final GeocodeRequests geocodeRequests;
    private final Geocoder geocoder;
    private final CustomerRepository customers;
    private final ServiceAreas serviceAreas;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param geocodeRequests the geocoding outbox
     * @param geocoder the outside geocoding service
     * @param customers where customers are kept
     * @param serviceAreas the company's service area, which a found point is checked against
     * @param clock the time of the job, and the Israel day the cap counts
     * @param auditLog the audit trail
     */
    public GeocodeCustomers(
            GeocodeRequests geocodeRequests,
            Geocoder geocoder,
            CustomerRepository customers,
            ServiceAreas serviceAreas,
            Clock clock,
            AuditLog auditLog) {
        this.geocodeRequests = geocodeRequests;
        this.geocoder = geocoder;
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
