package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RouteId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One report held for a dispatcher, an entity inside {@link Delivery} keyed by its {@link
 * HeldReportId} (BR-132, ADR-126). Only the delivery changes it: a decision moves its state, and
 * retention erases its outcome's personal data. {@link #report()} builds the public {@link
 * HeldReport} every caller sees.
 *
 * <p>It is mapped for JPA (ADR-126): its outcome is stored as {@link StoredOutcome}'s columns, and
 * its {@link HeldState} as a kind plus the dispatcher and the time the decided states carry.
 */
@Entity
@Table(schema = "liveops", name = "held_report")
class StoredHeldReport {

    /** Which {@link HeldState} a report is in. */
    private enum StateKind {
        OPEN,
        CONFIRMED,
        REFUSED_BY_DISPATCHER,
        OVERTAKEN
    }

    @Id
    private UUID id;

    @Embedded
    private StoredOutcome stated;

    private AccountId driver;

    private RouteId route;

    private RevisionNo revision;

    @Embedded
    private CorrectedTime time;

    @Enumerated(EnumType.STRING)
    private @Nullable CancellationKind overrides;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private @Nullable AccountId stateBy;

    private @Nullable Instant stateAt;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredHeldReport() {}

    /**
     * Stores a held report.
     *
     * @param report the report
     */
    StoredHeldReport(HeldReport report) {
        this.id = report.id().value();
        this.stated = new StoredOutcome(report.outcome());
        this.driver = report.driver();
        this.route = report.route();
        this.revision = report.revision();
        this.time = report.time();
        this.overrides = report.overrides().orElse(null);
        moveTo(report.state());
    }

    /**
     * The report as every caller sees it.
     *
     * @return the report
     * @throws com.dispatchflow.shared.StoredStateInvalid when its stored reason, proof or note is
     *     refused by its own rules
     */
    HeldReport report() {
        return new HeldReport(
                HeldReportId.of(id),
                stated.outcome(),
                driver,
                route,
                revision,
                time,
                Optional.ofNullable(overrides),
                state());
    }

    /**
     * Whether the report still waits for a decision.
     *
     * @return {@code true} while it is open
     */
    boolean isOpen() {
        return state == StateKind.OPEN;
    }

    /**
     * The report's ID.
     *
     * @return the ID
     */
    HeldReportId id() {
        return HeldReportId.of(id);
    }

    /**
     * Moves the report to another state, in place, clearing what the old one carried.
     *
     * @param next the new state
     */
    void moveTo(HeldState next) {
        Objects.requireNonNull(next, "next");
        stateBy = null;
        stateAt = null;
        state = switch (next) {
            case HeldState.Open open -> StateKind.OPEN;
            case HeldState.Confirmed confirmed -> {
                stateBy = confirmed.by();
                stateAt = confirmed.at();
                yield StateKind.CONFIRMED;
            }
            case HeldState.RefusedByDispatcher refused -> {
                stateBy = refused.by();
                stateAt = refused.at();
                yield StateKind.REFUSED_BY_DISPATCHER;
            }
            case HeldState.Overtaken overtaken -> {
                stateAt = overtaken.at();
                yield StateKind.OVERTAKEN;
            }
        };
    }

    /**
     * Replaces what the report states, as retention's erasure does.
     *
     * @param erased the outcome with its personal data removed
     */
    void restate(Outcome erased) {
        stated = new StoredOutcome(Objects.requireNonNull(erased, "erased"));
    }

    private HeldState state() {
        return switch (state) {
            case OPEN -> new HeldState.Open();
            case CONFIRMED -> new HeldState.Confirmed(decidedBy(), decidedAt());
            case REFUSED_BY_DISPATCHER -> new HeldState.RefusedByDispatcher(decidedBy(), decidedAt());
            case OVERTAKEN -> new HeldState.Overtaken(decidedAt());
        };
    }

    private AccountId decidedBy() {
        return Objects.requireNonNull(stateBy, "a dispatcher's decision names the dispatcher");
    }

    private Instant decidedAt() {
        return Objects.requireNonNull(stateAt, "every state but open has its time");
    }
}
