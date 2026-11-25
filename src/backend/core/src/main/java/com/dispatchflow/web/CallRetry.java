package com.dispatchflow.web;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Re-runs a call that lost a race, and writes the one refused audit record of each race it answers
 * (ADR-140 decision 6, ADR-121 decision 4, ADR-136, ADR-142). A save's {@code StaleVersion} on a
 * call that sent no precondition header, and a failed lock wait on any call, are run again, up to
 * {@link #MORE_TRIES} more times, each in new transactions, and only while no read-write
 * transaction of the try has committed ({@link CommitWatch}). Every other race answers at once. The
 * last try's exception is rethrown, and the kernel's handler answers it with the status {@link
 * Race} gives.
 *
 * <p>Any race that comes after a read-write commit of the same try is no longer answered as a race:
 * the change is stored, and a 503 would invite the client to send it again, as a 412 or a 409 would
 * after a reload. It is rethrown wrapped in {@link RaceAfterCommit}, which the kernel's handler
 * answers 500 {@code UNEXPECTED_ERROR} and logs at ERROR, with no refused audit record (decided on
 * 25 November 2026, ADR-142).
 *
 * <p>A use case never audits a race: this writes the one record, after the answer is chosen and
 * before it is thrown.
 */
@Component
class CallRetry {

    /** The tries after the first: four in all, then 503 (ADR-121 decision 4). */
    static final int MORE_TRIES = 3;

    private static final Logger log = LoggerFactory.getLogger(CallRetry.class);
    private static final String OPERATION = "operation";
    private static final String NEXT_TRY = "nextTry";

    private final Supplier<AuditLog> auditLog;
    private final Clock clock;

    /**
     * Creates the retry for Spring. The audit log is looked up only when a record is written, so
     * a missing one fails that call loudly: Audit's {@code AuditLogService} is no bean until #180
     * builds it, which replaces this lookup with plain injection.
     *
     * @param auditLog the audit log's provider
     * @param clock the time each record carries
     */
    @Autowired
    CallRetry(ObjectProvider<AuditLog> auditLog, Clock clock) {
        this(auditLog::getObject, clock);
    }

    /**
     * Creates the retry over a given audit log, for tests.
     *
     * @param auditLog the audit log, read when a record is written
     * @param clock the time each record carries
     */
    CallRetry(Supplier<AuditLog> auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Runs a call, again while it loses a race it may retry.
     *
     * @param request the request, which says whether the call sent a precondition header
     * @param operation the call's operation, for the log and the audit record
     * @param call the handler, invoked with the arguments already read
     * @return what the call returned
     * @throws Exception the last try's exception, checked ones included; an {@link Error} passes
     *     through untouched
     * @throws RaceAfterCommit when any race follows a read-write commit of the same try
     * @throws IllegalStateException when a race is to be audited but the MDC holds no trace ID,
     *     which means {@code TraceIdFilter} didn't run
     */
    @Nullable
    Object run(HttpServletRequest request, String operation, Call call) throws Exception {
        boolean preconditionSent = Race.preconditionSent(request);
        for (int tries = 1; ; tries++) {
            CommitWatch.Try attempt = new CommitWatch.Try();
            try {
                return ScopedValue.where(CommitWatch.CURRENT, attempt).call(call::call);
            } catch (Exception thrown) {
                Race race = Race.of(thrown, preconditionSent);
                if (race != Race.NONE && attempt.committed()) {
                    throw new RaceAfterCommit(thrown);
                }
                if (race == Race.RETRY && tries <= MORE_TRIES) {
                    log.atInfo()
                            .addKeyValue(OPERATION, operation)
                            .addKeyValue(NEXT_TRY, tries + 1)
                            .log("Retrying a call that lost a race");
                    continue;
                }
                if (race != Race.NONE) {
                    audit(operation, race, thrown);
                }
                throw thrown;
            }
        }
    }

    private void audit(String operation, Race race, Exception answered) {
        try {
            CallContext call =
                    new CallContext(Optional.empty(), Optional.empty(), Optional.empty(), operation, traceId());
            auditLog.get().refused(call, clock.now(), List.of(), race.code());
        } catch (RuntimeException failed) {
            failed.addSuppressed(answered);
            throw failed;
        }
    }

    private static String traceId() {
        String traceId = MDC.get(Problems.TRACE_ID);
        if (traceId == null) {
            throw new IllegalStateException("No trace ID in the MDC: the trace filter didn't run for this call");
        }
        return traceId;
    }

    /**
     * A race lost after the try stored its change: no race any more, so {@link Race#of} reads it
     * as none and the call answers 500. Its message names no value; the race is its cause.
     */
    static final class RaceAfterCommit extends IllegalStateException {

        private static final String MESSAGE = "A call lost a race after one of its transactions committed";

        RaceAfterCommit(Exception race) {
            super(MESSAGE, race);
        }
    }

    /** A handler's invocation, which may throw any exception the handler declares. */
    @FunctionalInterface
    interface Call {

        /**
         * Invokes the handler once.
         *
         * @return what the handler returned
         * @throws Exception what the handler threw
         */
        @Nullable
        Object call() throws Exception;
    }
}
