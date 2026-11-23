package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.delivery.history.CorrectionReason;
import com.dispatchflow.liveops.domain.delivery.history.Origin;
import com.dispatchflow.liveops.domain.delivery.history.OutcomeRecord;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The columns one {@link OutcomeRecord} is stored in, a row of its {@link Delivery}'s outcome table
 * (ADR-127 decision 3). A record has no ID of its own, and its outcome and its {@link Origin} are
 * sealed, so it is stored as this embeddable: the outcome's columns, and the origin as a kind plus
 * the nullable columns each kind needs. {@link #record()} builds the public record every caller
 * sees. Only {@code Delivery} makes one.
 */
@Embeddable
class StoredOutcomeRecord {

    /** Which {@link Origin} a record has. */
    private enum OriginKind {
        REPORTED,
        CONFIRMED_HELD,
        ON_BEHALF,
        CORRECTION
    }

    @Embedded
    private StoredOutcome stated;

    // "by" alone is an SQL keyword.
    @Column(name = "recorded_by")
    private AccountId by;

    private Instant factTime;

    @Enumerated(EnumType.STRING)
    private OriginKind origin;

    private @Nullable RouteId route;

    private @Nullable RevisionNo revision;

    @Embedded
    @AttributeOverride(name = "deviceTime", column = @Column(name = "report_device_time"))
    @AttributeOverride(name = "deviceClockAtSend", column = @Column(name = "report_device_clock_at_send"))
    @AttributeOverride(name = "receivedAt", column = @Column(name = "report_received_at"))
    private @Nullable CorrectedTime reportTime;

    @Enumerated(EnumType.STRING)
    private @Nullable CancellationKind servedAfter;

    private @Nullable HeldReportId confirmedReport;

    private @Nullable String correctionReason;

    private @Nullable Instant correctedAt;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredOutcomeRecord() {}

    /**
     * Stores a record.
     *
     * @param record the record
     */
    StoredOutcomeRecord(OutcomeRecord record) {
        this.stated = new StoredOutcome(record.outcome());
        this.by = record.by();
        this.factTime = record.factTime();
        this.origin = switch (record.origin()) {
            case Origin.Reported reported -> {
                this.route = reported.route();
                this.revision = reported.revision();
                this.reportTime = reported.time();
                this.servedAfter = reported.servedAfter().orElse(null);
                yield OriginKind.REPORTED;
            }
            case Origin.ConfirmedHeld confirmed -> {
                this.confirmedReport = confirmed.report();
                yield OriginKind.CONFIRMED_HELD;
            }
            case Origin.OnBehalf onBehalf -> {
                this.route = onBehalf.route();
                yield OriginKind.ON_BEHALF;
            }
            case Origin.Correction correction -> {
                this.correctionReason = correction.reason().text().orElse(null);
                this.correctedAt = correction.correctedAt();
                yield OriginKind.CORRECTION;
            }
        };
    }

    /**
     * The record these columns store.
     *
     * @return the record
     * @throws com.dispatchflow.shared.StoredStateInvalid when a stored reason, proof or note is
     *     refused by its own rules
     */
    OutcomeRecord record() {
        Origin built = switch (origin) {
            case REPORTED ->
                new Origin.Reported(
                        Objects.requireNonNull(route, "a report names its route"),
                        Objects.requireNonNull(revision, "a report names its revision"),
                        Objects.requireNonNull(reportTime, "a report has its time"),
                        Optional.ofNullable(servedAfter));
            case CONFIRMED_HELD ->
                new Origin.ConfirmedHeld(
                        Objects.requireNonNull(confirmedReport, "a confirmation names its held report"));
            case ON_BEHALF -> new Origin.OnBehalf(Objects.requireNonNull(route, "a record on behalf names its route"));
            case CORRECTION ->
                new Origin.Correction(
                        CorrectionReason.ofStored(Optional.ofNullable(correctionReason)),
                        Objects.requireNonNull(correctedAt, "a correction has its time"));
        };
        return new OutcomeRecord(stated.outcome(), by, factTime, built);
    }
}
