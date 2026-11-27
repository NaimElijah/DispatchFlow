package com.dispatchflow.audit.application.api;

import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.application.ApplicationRefusal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs one attempt of a use case and records the refusal that leaves it (NFR-23, ADR-150 decisions
 * 1 and 2). When a domain refusal, a {@code RuleViolation} or {@code RuleViolations}, or an {@link
 * ApplicationRefusal}, such as a {@code NotFound}, leaves the attempt, it writes {@link
 * AuditLog#refused} with the refusal's code and the records the attempt names, then rethrows the
 * refusal. The use case then answers the refusal, with its record stored: it throws the substitute
 * it answers with, such as {@code SignInRefused}, or runs a step of its own and rethrows it.
 *
 * <p>The use case calls it with no transaction open, around its own checks and its own {@code
 * <UseCase>Change}: never around another use case, which records its own refusals, nor around a
 * step after the commit, whose failure leaves the success standing. By the time a refusal leaves
 * the attempt, it has rolled the change's transaction back, so the record is written in a
 * transaction of its own (ADR-129 decision 5, ADR-149 decision 1). Called inside a transaction, the
 * refusal it rethrows would roll the record back with it, so no transactional class calls it
 * ({@code TransactionRules} rule 10).
 *
 * <p>It is a static helper over the port, not a method of the audit log. A method declared in the
 * audit log's transactional bean would run the {@code Change} inside the bean's transaction, and
 * the refusal's rollback would take the record with it. A default method of {@code AuditLog} would
 * call {@code refused} on the bean itself, past its transactional proxy, so the record's append
 * would find no transaction and be refused.
 *
 * <p>It records no race: a {@code StaleVersion} or a failed lock wait passes through untouched, for
 * the call wrapper, which records the one race it answers once its retries are spent (ADR-140
 * decision 6). A use case that answers a conflict itself records it with {@code AuditLog.refused}
 * (ADR-150 decision 3). Nor does it record a success: the {@code Change} records that in its own
 * transaction ({@code TransactionRules} rule 9).
 */
public final class Refusals {

    private Refusals() {}

    /**
     * Runs an attempt that returns a result, and records the refusal that leaves it.
     *
     * @param auditLog where the refused record is written
     * @param call who made the attempt, through which client, as which operation
     * @param at when the attempt was made
     * @param records the records the attempt names, in order; possibly none, and at most 500
     * @param attempt the use case's {@code Change}, and any check the use case makes before it
     * @param <T> what the attempt returns
     * @return what the attempt returned
     * @throws RuleViolation when one leaves the attempt, rethrown once its record is stored
     * @throws RuleViolations when they leave the attempt, rethrown once their record is stored
     * @throws RuntimeException an {@link ApplicationRefusal} that leaves the attempt, rethrown once
     *     its record is stored; the failed write of a refusal's record, thrown in the refusal's place
     *     with the refusal added as suppressed, so the call answers 500 (ADR-149 decision 1); or any
     *     other exception that leaves the attempt, a race included, untouched and unrecorded
     */
    public static <T> T recordedBy(
            AuditLog auditLog, CallContext call, Instant at, List<AuditedRecord> records, Supplier<T> attempt) {
        Objects.requireNonNull(auditLog, "auditLog");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(attempt, "attempt");
        try {
            return attempt.get();
        } catch (RuleViolation refusal) {
            throw recorded(auditLog, call, at, records, refusal.error().code(), refusal);
        } catch (RuleViolations refusals) {
            throw recorded(auditLog, call, at, records, refusals.summary().code(), refusals);
        } catch (RuntimeException thrown) {
            if (thrown instanceof ApplicationRefusal refusal) {
                throw recorded(auditLog, call, at, records, refusal.code(), thrown);
            }
            throw thrown;
        }
    }

    /**
     * Runs an attempt that returns nothing, and records the refusal that leaves it, as {@link
     * #recordedBy(AuditLog, CallContext, Instant, List, Supplier)} does.
     *
     * @param auditLog where the refused record is written
     * @param call who made the attempt, through which client, as which operation
     * @param at when the attempt was made
     * @param records the records the attempt names, in order; possibly none, and at most 500
     * @param attempt the use case's {@code Change}, and any check the use case makes before it
     * @throws RuleViolation when one leaves the attempt, rethrown once its record is stored
     * @throws RuleViolations when they leave the attempt, rethrown once their record is stored
     * @throws RuntimeException an {@link ApplicationRefusal} that leaves the attempt, rethrown once
     *     its record is stored; the failed write of a refusal's record, thrown in the refusal's place
     *     with the refusal added as suppressed; or any other exception that leaves the attempt,
     *     untouched and unrecorded
     */
    public static void recordedBy(
            AuditLog auditLog, CallContext call, Instant at, List<AuditedRecord> records, Runnable attempt) {
        Objects.requireNonNull(attempt, "attempt");
        recordedBy(auditLog, call, at, records, () -> {
            attempt.run();
            return Boolean.TRUE;
        });
    }

    /** Writes the refusal's record, and returns what to throw: the refusal, or the failed write. */
    private static RuntimeException recorded(
            AuditLog auditLog,
            CallContext call,
            Instant at,
            List<AuditedRecord> records,
            String code,
            RuntimeException refusal) {
        try {
            auditLog.refused(call, at, records, code);
        } catch (RuntimeException failed) {
            failed.addSuppressed(refusal);
            return failed;
        }
        return refusal;
    }
}
