package com.dispatchflow.liveops.domain.alert;

import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.LiveOpsError.AlertAlreadyAcknowledged;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Something a dispatcher must look at (DL §4.5, §17), in one list for every kind (FR-157). Any module
 * raises one through {@code RaiseAlert}. It is raised once per {@link DedupeKey}: alerts are
 * independent, so the repository's unique index on the key makes a second raise do nothing (DL §5,
 * ADR-107). Acknowledging records who and when, once (FR-260). Screens and push follow the commit,
 * so it raises no event (DL §7.1, ADR-083).
 *
 * <p>It is mapped for JPA (ADR-126): its key is its ID's UUID, its {@link AlertSubject} a kind plus
 * the nullable columns each kind names, its dedupe key a column of its own that the unique index
 * reads, and its {@link AlertState} a kind plus who acknowledged it and when. The plan's delivery
 * date, which the alert does not hold, is kept beside it by the persistence adapter.
 */
@Entity
@Table(schema = "liveops", name = "alert")
public class Alert extends AggregateRoot {

    /** Which {@link AlertState} an alert is in. */
    private enum StateKind {
        OPEN,
        ACKNOWLEDGED
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private AlertKind kind;

    private PlanId plan;

    private @Nullable RouteId route;

    private @Nullable StopId stop;

    private @Nullable RevisionNo revision;

    private @Nullable AccountId driver;

    @Enumerated(EnumType.STRING)
    private @Nullable DriverProblem problem;

    private @Nullable RouteId leftRoute;

    private @Nullable HeldReportId heldReport;

    @Enumerated(EnumType.STRING)
    private @Nullable RefusedFact refusedFact;

    private @Nullable RatingRequestId ratingRequest;

    // Derived from the subject and the ID, which never change, so the stored copy never drifts.
    @Column(updatable = false)
    private DedupeKey dedupeKey;

    private Instant raisedAt;

    @Enumerated(EnumType.STRING)
    private StateKind state;

    private @Nullable AccountId acknowledgedBy;

    private @Nullable Instant acknowledgedAt;

    /** For Hibernate, which sets the fields from the stored row and then runs {@link #checkStored}. */
    protected Alert() {}

    private Alert(AlertId id, AlertSubject subject, Instant raisedAt) {
        this.id = id.value();
        this.raisedAt = raisedAt;
        this.state = StateKind.OPEN;
        about(subject);
        this.dedupeKey = subject.dedupeKey(id);
    }

    /**
     * Raises an alert.
     *
     * @param id the new alert's ID
     * @param subject what it is about
     * @param now when it is raised
     * @return the alert, open, never saved
     */
    public static Alert raise(AlertId id, AlertSubject subject, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(now, "now");
        return new Alert(id, subject, now);
    }

    /**
     * The load check every root has (ADR-126 decision 8). It checks nothing: each value checked
     * itself when its converter rebuilt it, the migration's {@code CHECK} constraints keep each
     * kind's columns whole, and the kind and the key are derived from the subject, so no rule ties
     * the values together.
     */
    @PostLoad
    void checkStored() {
        // Deliberately empty: see the Javadoc.
    }

    /**
     * The alert's ID.
     *
     * @return the ID
     */
    public AlertId id() {
        return AlertId.of(id);
    }

    /**
     * The alert's kind.
     *
     * @return its subject's kind
     */
    public AlertKind kind() {
        return kind;
    }

    /**
     * What the alert is about.
     *
     * @return the subject, which names its plan and the IDs its kind keys on
     */
    public AlertSubject subject() {
        return switch (kind) {
            case STOP_AT_RISK -> new AlertSubject.StopAtRisk(plan, route(), stop(), revision());
            case DRIVER_PROBLEM ->
                new AlertSubject.DriverProblemReported(
                        plan, route(), driver(), Objects.requireNonNull(problem, "a driver's problem is named"));
            case ROUTE_NOT_STARTED -> new AlertSubject.RouteNotStarted(plan, route());
            case STOP_FAILED -> new AlertSubject.StopFailed(plan, route(), stop());
            case ROUTE_CHANGE_UNCONFIRMED -> new AlertSubject.RouteChangeUnconfirmed(plan, route(), revision());
            case LATE_REPORT -> new AlertSubject.LateReport(plan, route(), stop(), Optional.ofNullable(leftRoute));
            case REPORT_HELD ->
                new AlertSubject.ReportHeld(
                        plan, route(), stop(), Objects.requireNonNull(heldReport, "a held report's alert names it"));
            case REPORT_REFUSED ->
                new AlertSubject.ReportRefused(
                        plan,
                        route(),
                        stop(),
                        driver(),
                        Objects.requireNonNull(refusedFact, "a refusal's alert names what was refused"));
            case ROUTE_FINISHED_WITH_PENDING_STOPS -> new AlertSubject.RouteFinishedWithPendingStops(plan, route());
            case LOW_RATING ->
                new AlertSubject.LowRating(
                        plan, stop(), Objects.requireNonNull(ratingRequest, "a low rating's alert names its request"));
        };
    }

    /**
     * When the alert was raised.
     *
     * @return the time
     */
    public Instant raisedAt() {
        return raisedAt;
    }

    /**
     * Where the alert stands (FR-260).
     *
     * @return open, or acknowledged with who and when
     */
    public AlertState state() {
        return switch (state) {
            case OPEN -> new AlertState.Open();
            case ACKNOWLEDGED ->
                new AlertState.Acknowledged(
                        Objects.requireNonNull(acknowledgedBy, "an acknowledgement names its dispatcher"),
                        Objects.requireNonNull(acknowledgedAt, "an acknowledgement has its time"));
        };
    }

    /**
     * The key the repository's unique index stores (DL §5).
     *
     * @return the key its subject defines
     */
    public DedupeKey dedupeKey() {
        return dedupeKey;
    }

    /**
     * Whether the alert still waits for a dispatcher.
     *
     * @return {@code true} until it is acknowledged
     */
    public boolean isOpen() {
        return state == StateKind.OPEN;
    }

    /**
     * Acknowledges the alert, recording who and when (FR-260). Its key stays used, so the same
     * alert is never raised again (ADR-107).
     *
     * @param by the dispatcher
     * @param now when
     * @throws RuleViolation with {@link AlertAlreadyAcknowledged} when it was acknowledged before
     */
    public void acknowledge(AccountId by, Instant now) {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(now, "now");
        if (!isOpen()) {
            throw new RuleViolation(new AlertAlreadyAcknowledged(id()));
        }
        state = StateKind.ACKNOWLEDGED;
        acknowledgedBy = by;
        acknowledgedAt = now;
    }

    /** Sets the subject's columns: the kind, and the parts that kind names. */
    private void about(AlertSubject subject) {
        this.kind = subject.kind();
        this.plan = subject.plan();
        switch (subject) {
            case AlertSubject.StopAtRisk atRisk -> {
                route = atRisk.route();
                stop = atRisk.stop();
                revision = atRisk.revision();
            }
            case AlertSubject.DriverProblemReported reported -> {
                route = reported.route();
                driver = reported.driver();
                problem = reported.problem();
            }
            case AlertSubject.RouteNotStarted notStarted -> route = notStarted.route();
            case AlertSubject.StopFailed failed -> {
                route = failed.route();
                stop = failed.stop();
            }
            case AlertSubject.RouteChangeUnconfirmed unconfirmed -> {
                route = unconfirmed.route();
                revision = unconfirmed.revision();
            }
            case AlertSubject.LateReport late -> {
                route = late.servedOn();
                stop = late.stop();
                leftRoute = late.leftRoute().orElse(null);
            }
            case AlertSubject.ReportHeld held -> {
                route = held.route();
                stop = held.stop();
                heldReport = held.report();
            }
            case AlertSubject.ReportRefused refused -> {
                route = refused.route();
                stop = refused.stop();
                driver = refused.driver();
                refusedFact = refused.refused();
            }
            case AlertSubject.RouteFinishedWithPendingStops finished -> route = finished.route();
            case AlertSubject.LowRating low -> {
                stop = low.stop();
                ratingRequest = low.request();
            }
        }
    }

    private RouteId route() {
        return Objects.requireNonNull(route, "this kind of alert names its route");
    }

    private StopId stop() {
        return Objects.requireNonNull(stop, "this kind of alert names its stop");
    }

    private RevisionNo revision() {
        return Objects.requireNonNull(revision, "this kind of alert names its revision");
    }

    private AccountId driver() {
        return Objects.requireNonNull(driver, "this kind of alert names its driver");
    }
}
