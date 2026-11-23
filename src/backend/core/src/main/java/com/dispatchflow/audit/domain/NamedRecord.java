package com.dispatchflow.audit.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

/**
 * One of the records an audited operation names (NFR-93): its kind and its ID. An Admin filters
 * audit records by it (FR-126). Build one from the kernel's typed ID with an {@code of} factory,
 * which keeps the kind matched to the ID. The canonical constructor is for the persistence adapter,
 * which rebuilds a stored one, and it doesn't check that the kind matches the ID (ADR-110).
 *
 * <p>An audit record's stored row keeps each one in a row of its own, an element of its ordered
 * collection (ADR-127 decision 6). The constructor only checks that both parts are present, which
 * the table's {@code NOT NULL} columns keep, so Hibernate builds it through the canonical
 * constructor and no instantiator is registered for it.
 *
 * @param kind what the ID names
 * @param id the named thing's UUID
 */
@Embeddable
public record NamedRecord(@Enumerated(EnumType.STRING) RecordKind kind, UUID id) {

    /** Checks both are present. */
    public NamedRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    /**
     * Names an account, or the driver who shares its ID.
     *
     * @param id the account's ID
     * @return the named record
     */
    public static NamedRecord of(AccountId id) {
        return new NamedRecord(RecordKind.ACCOUNT, id.value());
    }

    /**
     * Names a session.
     *
     * @param id the session's ID
     * @return the named record
     */
    public static NamedRecord of(SessionId id) {
        return new NamedRecord(RecordKind.SESSION, id.value());
    }

    /**
     * Names a depot.
     *
     * @param id the depot's ID
     * @return the named record
     */
    public static NamedRecord of(DepotId id) {
        return new NamedRecord(RecordKind.DEPOT, id.value());
    }

    /**
     * Names a vehicle.
     *
     * @param id the vehicle's ID
     * @return the named record
     */
    public static NamedRecord of(VehicleId id) {
        return new NamedRecord(RecordKind.VEHICLE, id.value());
    }

    /**
     * Names a customer.
     *
     * @param id the customer's ID
     * @return the named record
     */
    public static NamedRecord of(CustomerId id) {
        return new NamedRecord(RecordKind.CUSTOMER, id.value());
    }

    /**
     * Names an order.
     *
     * @param id the order's ID
     * @return the named record
     */
    public static NamedRecord of(OrderId id) {
        return new NamedRecord(RecordKind.ORDER, id.value());
    }

    /**
     * Names an import.
     *
     * @param id the import's ID
     * @return the named record
     */
    public static NamedRecord of(ImportBatchId id) {
        return new NamedRecord(RecordKind.IMPORT_BATCH, id.value());
    }

    /**
     * Names a plan.
     *
     * @param id the plan's ID
     * @return the named record
     */
    public static NamedRecord of(PlanId id) {
        return new NamedRecord(RecordKind.PLAN, id.value());
    }

    /**
     * Names a route.
     *
     * @param id the route's ID
     * @return the named record
     */
    public static NamedRecord of(RouteId id) {
        return new NamedRecord(RecordKind.ROUTE, id.value());
    }

    /**
     * Names a stop.
     *
     * @param id the stop's ID
     * @return the named record
     */
    public static NamedRecord of(StopId id) {
        return new NamedRecord(RecordKind.STOP, id.value());
    }

    /**
     * Names a solve job.
     *
     * @param id the job's ID
     * @return the named record
     */
    public static NamedRecord of(JobId id) {
        return new NamedRecord(RecordKind.JOB, id.value());
    }

    /**
     * Names a re-optimization proposal.
     *
     * @param id the proposal's ID
     * @return the named record
     */
    public static NamedRecord of(ProposalId id) {
        return new NamedRecord(RecordKind.PROPOSAL, id.value());
    }

    /**
     * Names an alert.
     *
     * @param id the alert's ID
     * @return the named record
     */
    public static NamedRecord of(AlertId id) {
        return new NamedRecord(RecordKind.ALERT, id.value());
    }

    /**
     * Names a rating request.
     *
     * @param id the rating request's ID
     * @return the named record
     */
    public static NamedRecord of(RatingRequestId id) {
        return new NamedRecord(RecordKind.RATING_REQUEST, id.value());
    }
}
