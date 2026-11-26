package com.dispatchflow.audit.domain;

import com.dispatchflow.audit.domain.AuditError.TooManyNamedRecords;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.RuleViolation;
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
 * @param records the records the operation names, in the order given; possibly none, and at most
 *     {@link #MAX_RECORDS}
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
     * How long an audit record is kept, counted back on Israel's calendar from the time the
     * retention run asks: a year (NFR-258, ADR-075, ADR-110 point 2, ADR-149). The database
     * function that deletes audit records computes its own cutoff, {@code audit.retention_cutoff},
     * which must match {@link #retentionCutoff}.
     */
    public static final Period RETENTION = Period.ofYears(1);

    /**
     * The most records one audit record names. An operation on a whole plan names at most its plan,
     * its 15 routes and vehicles, and its 200 stops and their orders (NFR-04, NFR-05), 431 in all, so
     * the cap leaves room and still bounds what one call can write (ADR-114).
     */
    public static final int MAX_RECORDS = 500;

    /**
     * Checks every component is present, and copies the named records.
     *
     * @throws RuleViolation with {@link TooManyNamedRecords} when more than {@link #MAX_RECORDS}
     *     records are named
     */
    public AuditRecord {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(traceId, "traceId");
        if (records.size() > MAX_RECORDS) {
            throw new RuleViolation(new TooManyNamedRecords());
        }
        records = List.copyOf(records);
    }

    /**
     * Whether the record is older than its retention, so the nightly retention run may delete it
     * (NFR-258): whether it was made before {@link #retentionCutoff} of {@code now}. A record exactly
     * a year old is still kept, because only records older than a year are deleted (ADR-075).
     *
     * @param now when the retention run asks
     * @return true when {@link #at()} is before the retention cutoff of {@code now}
     */
    public boolean isPastRetention(Instant now) {
        return at.isBefore(retentionCutoff(now));
    }

    /**
     * The instant before which a record is past its retention at {@code now}: the same local time
     * in {@code Asia/Jerusalem}, {@link #RETENTION} earlier (ADR-149 decision 5). One cutoff serves
     * every record, so an older record is never kept longer than a newer one, and the database's
     * {@code audit.retention_cutoff} computes the same instant by the same steps.
     *
     * <p>A year before 29 February is 28 February. A local time the clocks skipped a year earlier
     * moves forward by the skipped hour. A local time they repeated takes standard time, the later of
     * its two instants, as PostgreSQL reads such a time: {@code java.time} would otherwise keep the
     * offset of {@code now}, and from a summer time pick the earlier one.
     *
     * @param now when the retention run asks
     * @return the cutoff instant
     * @throws NullPointerException when {@code now} is null
     */
    public static Instant retentionCutoff(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.atZone(IsraelDateTime.ZONE)
                .minus(RETENTION)
                .withLaterOffsetAtOverlap()
                .toInstant();
    }
}
