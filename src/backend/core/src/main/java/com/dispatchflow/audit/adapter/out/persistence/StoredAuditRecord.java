package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ClientApp;
import com.dispatchflow.audit.domain.DeclaredApp;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.NamedRecord;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One stored {@link AuditRecord}, a record appended and never changed (DL §3.2, ADR-127 decision 6).
 * The record has no ID, so its row is keyed by a random UUID that nothing outside the table sees,
 * and two records with the same content are two rows. Its outcome is a kind beside the code only a
 * refusal has, and its named records are an ordered collection.
 *
 * <p>The core writes it and never reads it back; the mapping lets Hibernate check the tables against
 * it when the application starts.
 */
@Entity
@Immutable
@Table(schema = "audit", name = "audit_record")
class StoredAuditRecord {

    /** Which {@link AuditOutcome} a record has. */
    enum Outcome {
        /** The attempt succeeded. */
        SUCCEEDED,
        /** The attempt was refused, with the row's error code. */
        REFUSED
    }

    @Id
    private UUID id;

    private Instant at;

    // PostgreSQL reserves "user", so the acting user's column is named for the account it is.
    private @Nullable AccountId account;

    private @Nullable SessionId session;

    @Enumerated(EnumType.STRING)
    private @Nullable ClientApp app;

    private @Nullable AppVersion appVersion;

    private OperationName operation;

    @ElementCollection
    @CollectionTable(
            schema = "audit",
            name = "audit_record_named_record",
            joinColumns = @JoinColumn(name = "audit_record_id"))
    @OrderColumn(name = "position", nullable = false)
    @AttributeOverride(name = "id", column = @Column(name = "record_id"))
    private List<NamedRecord> records = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    private @Nullable ErrorCode errorCode;

    private TraceId traceId;

    /** For Hibernate. */
    protected StoredAuditRecord() {}

    /**
     * Stores a record.
     *
     * @param id the row's key, which the adapter draws
     * @param record the record
     */
    StoredAuditRecord(UUID id, AuditRecord record) {
        this.id = id;
        this.at = record.at();
        this.account = record.user().orElse(null);
        this.session = record.session().orElse(null);
        this.app = record.app().map(DeclaredApp::app).orElse(null);
        this.appVersion = record.app().map(DeclaredApp::version).orElse(null);
        this.operation = record.operation();
        this.records.addAll(record.records());
        switch (record.outcome()) {
            case AuditOutcome.Succeeded succeeded -> this.outcome = Outcome.SUCCEEDED;
            case AuditOutcome.Refused(ErrorCode code) -> {
                this.outcome = Outcome.REFUSED;
                this.errorCode = code;
            }
        }
        this.traceId = record.traceId();
    }
}
