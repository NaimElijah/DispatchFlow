package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.GeocodeRequests;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;

/**
 * A dispatcher confirms an import preview within its hour (FR-204, FR-399, ADR-086): the use case
 * reads the {@code ImportContext} again, because customers, references and capacity may have
 * changed since the preview, and calls {@code ImportBatch.confirm}. Every row is checked again,
 * and any error imports nothing (FR-204). In one transaction it saves the batch, adds every new
 * customer and order the confirmation returns (DL §6.1 point 1), and, from M7, writes a geocoding
 * request for each new customer (FR-12, FR-311). A Windows-1255 file needs the dispatcher's
 * confirmation of the decoded preview (FR-313).
 *
 * <p>Built in M2.
 */
public final class ConfirmOrderImport {

    private final ImportBatchRepository imports;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final GeocodeRequests geocodeRequests;
    private final Company company;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param imports where imports are kept
     * @param customers where customers are kept, for each address's candidates and the new ones
     * @param orders where orders are kept, for each date's capacity and the new ones
     * @param geocodeRequests the geocoding outbox, for the new customers
     * @param company Access's company profile, for a new customer's defaults
     * @param ids the source of the new customers' and orders' IDs
     * @param clock today and the time of the confirmation
     * @param auditLog the audit trail
     */
    public ConfirmOrderImport(
            ImportBatchRepository imports,
            CustomerRepository customers,
            OrderRepository orders,
            GeocodeRequests geocodeRequests,
            Company company,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.imports = imports;
        this.customers = customers;
        this.orders = orders;
        this.geocodeRequests = geocodeRequests;
        this.company = company;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the import.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param batch the import
     * @param encodingConfirmed whether the dispatcher confirmed the decoded preview (FR-313)
     * @param expected the version of the preview the dispatcher confirmed (BR-76)
     * @return the orders and the customers the import created
     * @throws NotFound when no import has the ID, or no company profile exists yet
     * @throws RuleViolation with {@code ImportAlreadyConfirmed}, {@code ImportDiscarded} or {@code
     *     ImportExpired}, then with {@code EncodingNotConfirmed} (FR-313)
     * @throws RuleViolations with the summary {@code ImportHasErrors} and a {@code RowRefused} for
     *     each error of each row, in row order (FR-203, FR-204)
     * @throws StaleVersion when the import changed since {@code expected}
     */
    public ImportOutcome confirm(Actor actor, ImportBatchId batch, boolean encodingConfirmed, Version expected) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * What a confirmed import created, by ID only, so it holds no personal data (FR-204).
     *
     * <p>Built in M2.
     *
     * @param orders the new orders, in row order
     * @param newCustomers the customers the import created, in the order of their first rows
     *     (FR-311)
     */
    public record ImportOutcome(List<OrderId> orders, List<CustomerId> newCustomers) {

        /** Copies the lists. */
        public ImportOutcome {
            orders = List.copyOf(orders);
            newCustomers = List.copyOf(newCustomers);
        }
    }
}
