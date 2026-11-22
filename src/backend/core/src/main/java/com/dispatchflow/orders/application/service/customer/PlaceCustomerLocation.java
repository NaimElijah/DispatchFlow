package com.dispatchflow.orders.application.service.customer;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.ServiceAreas;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A dispatcher places a customer's location by hand, to fix a bad match or when geocoding found
 * none (FR-14, FR-218): {@code Customer.placeLocation}. The point wins over every later geocoding
 * result (BR-13), serves every future order (FR-215), clears the address's flags (FR-217), and
 * must lie inside the service area (FR-320).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M7.
 */
public final class PlaceCustomerLocation {

    private final CustomerRepository customers;
    private final ServiceAreas serviceAreas;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept
     * @param serviceAreas the company's service area
     * @param clock the time of the change, for the audit record
     * @param auditLog the audit trail
     */
    public PlaceCustomerLocation(
            CustomerRepository customers, ServiceAreas serviceAreas, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.serviceAreas = serviceAreas;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Places the location.
     *
     * <p>Built in M7.
     *
     * @param actor the dispatcher
     * @param customer the customer
     * @param point the point placed on the map
     * @param expected the version the dispatcher changed, from the customer's page ({@code
     *     CustomerView}) or the flagged map ({@code FlaggedCustomer}) (BR-76)
     * @throws NotFound when no customer has the ID
     * @throws RuleViolation with {@code CustomerAnonymized}, {@code CustomerRetired} or {@code
     *     OutsideServiceArea} (FR-320), in that order
     * @throws StaleVersion when the customer changed since {@code expected}
     */
    public void place(Actor actor, CustomerId customer, GeoPoint point, Version expected) {
        throw new UnsupportedOperationException("Built in M7");
    }
}
