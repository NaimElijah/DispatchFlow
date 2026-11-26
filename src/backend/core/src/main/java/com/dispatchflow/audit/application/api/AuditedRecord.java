package com.dispatchflow.audit.application.api;

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
import java.util.Objects;
import java.util.UUID;

/**
 * One of the records an audited operation names (NFR-93): its kind and its ID. A use case builds it
 * from the kernel's typed ID with an {@code of} factory, which keeps the kind matched to the ID
 * (ADR-110).
 *
 * @param kind what the ID names
 * @param id the named thing's UUID
 */
public record AuditedRecord(AuditedKind kind, UUID id) {

    /** Checks both parts are present. */
    public AuditedRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    /**
     * Names an account, or the driver who shares its ID.
     *
     * @param id the account's ID
     * @return the audited record
     */
    public static AuditedRecord of(AccountId id) {
        return new AuditedRecord(AuditedKind.ACCOUNT, id.value());
    }

    /**
     * Names a session.
     *
     * @param id the session's ID
     * @return the audited record
     */
    public static AuditedRecord of(SessionId id) {
        return new AuditedRecord(AuditedKind.SESSION, id.value());
    }

    /**
     * Names a depot.
     *
     * @param id the depot's ID
     * @return the audited record
     */
    public static AuditedRecord of(DepotId id) {
        return new AuditedRecord(AuditedKind.DEPOT, id.value());
    }

    /**
     * Names a vehicle.
     *
     * @param id the vehicle's ID
     * @return the audited record
     */
    public static AuditedRecord of(VehicleId id) {
        return new AuditedRecord(AuditedKind.VEHICLE, id.value());
    }

    /**
     * Names a customer.
     *
     * @param id the customer's ID
     * @return the audited record
     */
    public static AuditedRecord of(CustomerId id) {
        return new AuditedRecord(AuditedKind.CUSTOMER, id.value());
    }

    /**
     * Names an order.
     *
     * @param id the order's ID
     * @return the audited record
     */
    public static AuditedRecord of(OrderId id) {
        return new AuditedRecord(AuditedKind.ORDER, id.value());
    }

    /**
     * Names an import.
     *
     * @param id the import's ID
     * @return the audited record
     */
    public static AuditedRecord of(ImportBatchId id) {
        return new AuditedRecord(AuditedKind.IMPORT_BATCH, id.value());
    }

    /**
     * Names a plan.
     *
     * @param id the plan's ID
     * @return the audited record
     */
    public static AuditedRecord of(PlanId id) {
        return new AuditedRecord(AuditedKind.PLAN, id.value());
    }

    /**
     * Names a route.
     *
     * @param id the route's ID
     * @return the audited record
     */
    public static AuditedRecord of(RouteId id) {
        return new AuditedRecord(AuditedKind.ROUTE, id.value());
    }

    /**
     * Names a stop.
     *
     * @param id the stop's ID
     * @return the audited record
     */
    public static AuditedRecord of(StopId id) {
        return new AuditedRecord(AuditedKind.STOP, id.value());
    }

    /**
     * Names a solve job.
     *
     * @param id the job's ID
     * @return the audited record
     */
    public static AuditedRecord of(JobId id) {
        return new AuditedRecord(AuditedKind.JOB, id.value());
    }

    /**
     * Names a re-optimization proposal.
     *
     * @param id the proposal's ID
     * @return the audited record
     */
    public static AuditedRecord of(ProposalId id) {
        return new AuditedRecord(AuditedKind.PROPOSAL, id.value());
    }

    /**
     * Names an alert.
     *
     * @param id the alert's ID
     * @return the audited record
     */
    public static AuditedRecord of(AlertId id) {
        return new AuditedRecord(AuditedKind.ALERT, id.value());
    }

    /**
     * Names a rating request.
     *
     * @param id the rating request's ID
     * @return the audited record
     */
    public static AuditedRecord of(RatingRequestId id) {
        return new AuditedRecord(AuditedKind.RATING_REQUEST, id.value());
    }
}
