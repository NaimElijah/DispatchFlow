package com.dispatchflow.audit.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.SessionId;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The one record every API call that changes something, and every sign-in, refresh and sign-out,
 * leaves, whether it succeeded or was refused (NFR-23, ADR-110). It holds exactly NFR-93's fields,
 * each an ID, a code, an enum, a number or an instant, so it never carries personal data or a
 * secret (NFR-94).
 *
 * <p>It is a record, not an aggregate (DL §3.2): immutable, with no version and no event, and
 * appended through the {@code AuditLog} port in the transaction of the change it describes
 * (DL §4.8). Writing a refused attempt so that its rollback doesn't lose it is the application
 * layer's job (ADR-110).
 *
 * @param at when the attempt was made
 * @param user the acting user's account, when known
 * @param session the acting session, when known; recorded whether or not the user is
 * @param app the app and its version as the client declared them, when a client made the call
 * @param operation the operation attempted
 * @param records the records the operation names, in the order given; possibly none
 * @param outcome whether it succeeded, or the code it was refused with
 * @param traceId the request's trace ID
 */
public record AuditRecord(
        Instant at,
        Optional<AccountId> user,
        Optional<SessionId> session,
        Optional<DeclaredApp> app,
        OperationName operation,
        List<NamedRecord> records,
        AuditOutcome outcome,
        TraceId traceId) {

    /**
     * How long an audit record is kept, counted on Israel's calendar from {@link #at()}: a year
     * (NFR-258, ADR-075, ADR-110 point 2). The database function that deletes audit records
     * computes its own cutoff, and must match this period.
     */
    public static final Period RETENTION = Period.ofYears(1);

    /** Checks every component is present, and copies the named records. */
    public AuditRecord {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(traceId, "traceId");
        records = List.copyOf(records);
    }

    /**
     * Whether the record is older than its retention, so the nightly retention run may delete it
     * (NFR-258). A record exactly a year old is still kept, because only records older than a year
     * are deleted (ADR-075).
     *
     * @param now when the retention run asks
     * @return true once {@code now} is after the same local time a year after {@link #at()} in
     *     {@code Asia/Jerusalem}
     */
    public boolean isPastRetention(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant retentionEnds = at.atZone(IsraelDateTime.ZONE).plus(RETENTION).toInstant();
        return now.isAfter(retentionEnds);
    }
}
