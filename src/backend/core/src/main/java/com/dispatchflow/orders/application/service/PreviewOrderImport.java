package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.access.application.api.CompanyNotSetUp;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.FileEncoding;
import com.dispatchflow.orders.domain.FileHash;
import com.dispatchflow.orders.domain.ImportRow;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import java.util.List;

/**
 * A dispatcher uploads an order file, which is checked and kept as a preview for an hour (FR-10,
 * FR-203, FR-399, ADR-086). The file reader in the web adapter decodes it (FR-61, FR-313), refuses
 * a file it can't read (FR-209, FR-314), reads CSV and, from M7, Excel (FR-138), and parses each
 * row against the fixed columns (FR-309, FR-364). The use case builds the {@code ImportContext}:
 * for each distinct normalized address in the file, the active customers that have it (FR-60,
 * FR-205, FR-310 to FR-312, FR-398); for each distinct date, its capacity under the date's lock;
 * and the company's defaults for a customer a row creates (FR-311). It then calls {@code
 * ImportBatch.preview}, which keeps every error with its row. A preview is never refused: its
 * errors refuse the confirmation.
 *
 * <p>Built in M2.
 */
public final class PreviewOrderImport {

    private final ImportBatchRepository imports;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final Company company;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param imports where imports are kept
     * @param customers where customers are kept, for each address's candidates
     * @param orders where orders are kept, for each date's capacity
     * @param company Access's company profile, for a new customer's defaults
     * @param ids the source of the new import's ID
     * @param clock today and the time of the preview
     * @param auditLog the audit trail
     */
    public PreviewOrderImport(
            ImportBatchRepository imports,
            CustomerRepository customers,
            OrderRepository orders,
            Company company,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.imports = imports;
        this.customers = customers;
        this.orders = orders;
        this.company = company;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Previews the file.
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param hash the file's hash
     * @param encoding how the reader decoded it
     * @param rows every row the reader parsed, each read whole or with its fields' refusals
     * @return the new preview's ID
     * @throws CompanyNotSetUp when no Admin has set the company profile up yet
     */
    public ImportBatchId preview(Actor actor, FileHash hash, FileEncoding encoding, List<ImportRow> rows) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
