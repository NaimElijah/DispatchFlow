package com.dispatchflow.liveops.domain.alert;

import com.dispatchflow.shared.AlertId;
import java.time.Instant;

/**
 * What a test sees of an {@link Alert}, read through its getters: a value that compares by every
 * part. Tests compare an alert before and after a refused command, or after a load with what was
 * added, through it. It leaves the version out, which tests check on their own.
 *
 * @param id the alert's ID
 * @param kind its kind
 * @param subject what it is about
 * @param dedupeKey the key that makes it once
 * @param raisedAt when it was raised
 * @param state open or acknowledged
 */
public record AlertDescription(
        AlertId id, AlertKind kind, AlertSubject subject, DedupeKey dedupeKey, Instant raisedAt, AlertState state) {

    /**
     * Describes an alert.
     *
     * @param alert the alert
     * @return what its getters give
     */
    public static AlertDescription of(Alert alert) {
        return new AlertDescription(
                alert.id(), alert.kind(), alert.subject(), alert.dedupeKey(), alert.raisedAt(), alert.state());
    }
}
