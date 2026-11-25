package com.dispatchflow.web;

import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * What a call that lost a race for a record has become (ADR-140 decisions 5 and 6), with the
 * status and the code it answers, so the call wrapper, which decides whether to retry, and the
 * kernel's handler, which answers, never disagree.
 *
 * <p>It reads the type of the exception thrown and never its causes: {@code VersionedSave} keeps a
 * failed lock wait as the cause of the {@code StaleVersion} it throws, and a call that sent {@code
 * If-Match} must still answer 412 (ADR-136). So a race that {@link CallRetry} wraps in {@code
 * RaceAfterCommit}, because it came after the try stored its change, is {@link #NONE} and answers
 * 500 (ADR-142).
 */
enum Race {

    /** No race: the exception is something else, answered as unexpected. */
    NONE(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR),

    /**
     * A save's conflict on a call with no version, or a failed lock wait: retried, then 503, while
     * no read-write transaction of the try has committed.
     */
    RETRY(HttpStatus.SERVICE_UNAVAILABLE, WebCodes.TRY_AGAIN_LATER),

    /** Any {@code StaleVersion} on a call that sent {@code If-Match} or {@code If-None-Match}. */
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED, StaleVersion.CODE),

    /** A {@code StaleVersion} that compared a version the request sent in its body. */
    STALE_BODY_VERSION(HttpStatus.CONFLICT, StaleVersion.CODE);

    /**
     * The seconds a 503's {@code Retry-After} asks a client to wait: ADR-136's {@code
     * lock_timeout=2s}, the longest a try waits for a lock. A test reads the property and checks
     * the two agree.
     */
    static final int RETRY_AFTER_SECONDS = 2;

    private final HttpStatus status;
    private final String code;

    Race(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    /**
     * Classifies a thrown exception by its own type.
     *
     * @param thrown the exception the call threw
     * @param preconditionSent whether the call sent {@code If-Match} or {@code If-None-Match}
     * @return the race, or {@link #NONE} when the exception is no race
     */
    static Race of(Throwable thrown, boolean preconditionSent) {
        if (thrown instanceof StaleVersion stale) {
            if (preconditionSent) {
                return PRECONDITION_FAILED;
            }
            return stale.comparedTheRequestsVersion() ? STALE_BODY_VERSION : RETRY;
        }
        if (thrown instanceof PessimisticLockingFailureException
                || thrown instanceof PessimisticLockException
                || thrown instanceof LockTimeoutException) {
            return RETRY;
        }
        return NONE;
    }

    /**
     * Whether the request sent a precondition header.
     *
     * @param request the request
     * @return {@code true} when it carries {@code If-Match} or {@code If-None-Match}
     */
    static boolean preconditionSent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.IF_MATCH) != null || request.getHeader(HttpHeaders.IF_NONE_MATCH) != null;
    }

    /**
     * The status this race answers.
     *
     * @return the status
     */
    HttpStatus status() {
        return status;
    }

    /**
     * The code this race answers, and its audit record's error code.
     *
     * @return the code
     */
    String code() {
        return code;
    }
}
