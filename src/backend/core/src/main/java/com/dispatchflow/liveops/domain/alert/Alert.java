package com.dispatchflow.liveops.domain.alert;

import com.dispatchflow.liveops.domain.LiveOpsError.AlertAlreadyAcknowledged;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RuleViolation;
import java.time.Instant;
import java.util.Objects;

/**
 * Something a dispatcher must look at (DL §4.5, §17), in one list for every kind (FR-157). Any module
 * raises one through {@code RaiseAlert}. It is raised once per {@link DedupeKey}: alerts are
 * independent, so the repository's unique index on the key makes a second raise do nothing (DL §5,
 * ADR-107). Acknowledging records who and when, once (FR-260). Screens and push follow the commit,
 * so it raises no event (DL §7.1, ADR-083).
 */
public final class Alert extends AggregateRoot {

    private final AlertId id;
    private final AlertSubject subject;
    private final Instant raisedAt;
    private AlertState state;

    private Alert(AlertSnapshot stored) {
        super(stored.version());
        this.id = stored.id();
        this.subject = stored.subject();
        this.raisedAt = stored.raisedAt();
        this.state = stored.state();
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
        return new Alert(new AlertSnapshot(id, null, subject, now, new AlertState.Open()));
    }

    /**
     * Rebuilds a stored alert; only the persistence adapter calls it (DL §3.5). Its types leave no
     * at-rest invariant to check: the kind and the key are derived from the subject.
     *
     * @param stored the stored state
     * @return the alert at the stored version
     */
    public static Alert restore(AlertSnapshot stored) {
        return new Alert(Objects.requireNonNull(stored, "stored"));
    }

    /**
     * The alert's state, for the repository to store.
     *
     * @return the stored form
     */
    public AlertSnapshot snapshot() {
        return new AlertSnapshot(id, savedVersion(), subject, raisedAt, state);
    }

    /**
     * The alert's ID.
     *
     * @return the ID
     */
    public AlertId id() {
        return id;
    }

    /**
     * The alert's kind.
     *
     * @return its subject's kind
     */
    public AlertKind kind() {
        return subject.kind();
    }

    /**
     * The key the repository's unique index stores (DL §5).
     *
     * @return the key its subject defines
     */
    public DedupeKey dedupeKey() {
        return subject.dedupeKey(id);
    }

    /**
     * Whether the alert still waits for a dispatcher.
     *
     * @return {@code true} until it is acknowledged
     */
    public boolean isOpen() {
        return state instanceof AlertState.Open;
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
            throw new RuleViolation(new AlertAlreadyAcknowledged(id));
        }
        state = new AlertState.Acknowledged(by, now);
    }
}
