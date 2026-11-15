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
 * <p>Built in M0.
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
     * <p>Built in M0.
     *
     * @param id the account's ID
     * @return the audited record
     */
    public static AuditedRecord of(AccountId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a session.
     *
     * <p>Built in M0.
     *
     * @param id the session's ID
     * @return the audited record
     */
    public static AuditedRecord of(SessionId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a depot.
     *
     * <p>Built in M0.
     *
     * @param id the depot's ID
     * @return the audited record
     */
    public static AuditedRecord of(DepotId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a vehicle.
     *
     * <p>Built in M0.
     *
     * @param id the vehicle's ID
     * @return the audited record
     */
    public static AuditedRecord of(VehicleId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a customer.
     *
     * <p>Built in M0.
     *
     * @param id the customer's ID
     * @return the audited record
     */
    public static AuditedRecord of(CustomerId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names an order.
     *
     * <p>Built in M0.
     *
     * @param id the order's ID
     * @return the audited record
     */
    public static AuditedRecord of(OrderId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names an import.
     *
     * <p>Built in M0.
     *
     * @param id the import's ID
     * @return the audited record
     */
    public static AuditedRecord of(ImportBatchId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a plan.
     *
     * <p>Built in M0.
     *
     * @param id the plan's ID
     * @return the audited record
     */
    public static AuditedRecord of(PlanId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a route.
     *
     * <p>Built in M0.
     *
     * @param id the route's ID
     * @return the audited record
     */
    public static AuditedRecord of(RouteId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a stop.
     *
     * <p>Built in M0.
     *
     * @param id the stop's ID
     * @return the audited record
     */
    public static AuditedRecord of(StopId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a solve job.
     *
     * <p>Built in M0.
     *
     * @param id the job's ID
     * @return the audited record
     */
    public static AuditedRecord of(JobId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a re-optimization proposal.
     *
     * <p>Built in M0.
     *
     * @param id the proposal's ID
     * @return the audited record
     */
    public static AuditedRecord of(ProposalId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names an alert.
     *
     * <p>Built in M0.
     *
     * @param id the alert's ID
     * @return the audited record
     */
    public static AuditedRecord of(AlertId id) {
        throw new UnsupportedOperationException("Built in M0");
    }

    /**
     * Names a rating request.
     *
     * <p>Built in M0.
     *
     * @param id the rating request's ID
     * @return the audited record
     */
    public static AuditedRecord of(RatingRequestId id) {
        throw new UnsupportedOperationException("Built in M0");
    }
}
