package com.dispatchflow.audit.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedKind;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ClientApp;
import com.dispatchflow.audit.domain.DeclaredApp;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.RecordKind;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.RuleViolation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit's implementation of {@link AuditLog}: it translates the call into Audit's own {@code
 * AuditRecord} and appends it (DL §2.3 rule 1, NFR-93, ADR-110). Every part it keeps is an ID, a
 * code, an enum, a number or an instant, so no personal data or secret reaches a record (NFR-94).
 *
 * <p>Its transaction is the default one: {@link #succeeded} joins the change's, and {@link
 * #refused}, called with none open, opens its own (ADR-129 decision 5). Audit's domain refuses a
 * malformed part before anything is appended, and the service rethrows that refusal as an {@code
 * IllegalStateException}: every part comes from the server, so a malformed one is a bug, which
 * answers 500, and {@code Refusals}, which records the refusal that leaves a use case's {@code
 * Change}, never takes it for the change's own (ADR-149 decision 1, ADR-150 decision 1). A change
 * whose record is refused rolls back rather than commit without it (NFR-23).
 */
@Transactional
public class AuditLogService implements AuditLog {

    private final AuditRecords auditRecords;

    /**
     * Creates the service.
     *
     * @param auditRecords where audit records are appended; the adapter draws each row's key
     */
    public AuditLogService(AuditRecords auditRecords) {
        this.auditRecords = Objects.requireNonNull(auditRecords, "auditRecords");
    }

    @Override
    public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
        append(call, at, records, AuditOutcome::succeeded);
    }

    @Override
    public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
        Objects.requireNonNull(errorCode, "errorCode");
        append(call, at, records, () -> AuditOutcome.refused(ErrorCode.of(errorCode)));
    }

    private void append(CallContext call, Instant at, List<AuditedRecord> records, Supplier<AuditOutcome> outcome) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(records, "records");
        AuditRecord record;
        try {
            record = new AuditRecord(
                    at,
                    call.user(),
                    call.session(),
                    call.client().flatMap(AuditLogService::declared),
                    OperationName.of(call.operation()),
                    records.stream().map(AuditLogService::named).toList(),
                    outcome.get(),
                    TraceId.of(call.traceId()));
        } catch (RuleViolation malformed) {
            throw new IllegalStateException("Audit refused a part of the record the server built", malformed);
        }
        auditRecords.append(record);
    }

    // A version that doesn't parse leaves the app out too, since the two travel as a pair (ADR-110).
    private static Optional<DeclaredApp> declared(ClientRelease client) {
        return AppVersion.parseIfValid(client.version()).map(version -> new DeclaredApp(app(client.kind()), version));
    }

    private static ClientApp app(ClientKind kind) {
        return switch (kind) {
            case WEB -> ClientApp.WEB;
            case PHONE -> ClientApp.PHONE;
        };
    }

    private static NamedRecord named(AuditedRecord record) {
        return new NamedRecord(kind(record.kind()), record.id());
    }

    private static RecordKind kind(AuditedKind kind) {
        return switch (kind) {
            case ACCOUNT -> RecordKind.ACCOUNT;
            case SESSION -> RecordKind.SESSION;
            case DEPOT -> RecordKind.DEPOT;
            case VEHICLE -> RecordKind.VEHICLE;
            case CUSTOMER -> RecordKind.CUSTOMER;
            case ORDER -> RecordKind.ORDER;
            case IMPORT_BATCH -> RecordKind.IMPORT_BATCH;
            case PLAN -> RecordKind.PLAN;
            case ROUTE -> RecordKind.ROUTE;
            case STOP -> RecordKind.STOP;
            case JOB -> RecordKind.JOB;
            case PROPOSAL -> RecordKind.PROPOSAL;
            case ALERT -> RecordKind.ALERT;
            case RATING_REQUEST -> RecordKind.RATING_REQUEST;
        };
    }
}
