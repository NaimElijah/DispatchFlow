package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequest;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.application.port.out.ServiceAreas;
import com.dispatchflow.orders.domain.GeocodeResult;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link GeocodeCustomers}, which hold none of its outside calls (ADR-129
 * decision 3): one claims the due requests, and each customer's answer is recorded in one of its
 * own, with its audit record, so one stale save doesn't hold back the rest.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class GeocodeCustomersChange {

    private final GeocodeRequests geocodeRequests;
    private final CustomerRepository customers;
    private final ServiceAreas serviceAreas;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param geocodeRequests the geocoding outbox
     * @param customers where customers are kept
     * @param serviceAreas the company's service area, which a found point is checked against
     * @param auditLog the audit trail
     */
    public GeocodeCustomersChange(
            GeocodeRequests geocodeRequests,
            CustomerRepository customers,
            ServiceAreas serviceAreas,
            AuditLog auditLog) {
        this.geocodeRequests = geocodeRequests;
        this.customers = customers;
        this.serviceAreas = serviceAreas;
        this.auditLog = auditLog;
    }

    /**
     * Claims the requests whose next try is due, at most what is left of the Israel day's {@value
     * GeocodeRequests#DAILY_LOOKUPS} lookups (NFR-135). Each is marked tried at {@code now}, so a
     * request the job doesn't finish comes due again (FR-64).
     *
     * <p>To Be Implemented.
     *
     * @param now the time of the job
     * @return the claimed requests, oldest first; none once the day's cap is reached
     */
    public List<GeocodeRequest> claimDue(Instant now) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Records one request's answer: {@code Customer.recordGeocode} with the service area, which
     * flags the doubtful ones (FR-63), the customer's save with its audit record, and the request
     * done. An answer for an address the customer no longer has changes no customer (BR-104).
     *
     * <p>To Be Implemented.
     *
     * @param request the claimed request
     * @param result the answer built from the geocoder's and the road network's, or {@code
     *     GeocodeResult.GaveUp} after 24 hours of retries (FR-64)
     * @param now the time of the job
     * @param call the job's server step
     * @throws StaleVersion when the customer changed between its read and its save; the request
     *     stays, and comes due again
     */
    public void record(GeocodeRequest request, GeocodeResult result, Instant now, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
