package com.dispatchflow.web;

import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.adapter.in.web.HandlerOrder;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers the kernel's errors with the statuses of ADR-140 decision 5. It chooses a status by the
 * error's {@code code}, so {@code web} imports no module's error types: a refused rule answers 422
 * unless {@link #OVERRIDES} names its code. Each module's own application exceptions go to that
 * module's handler, which runs first ({@link HandlerOrder}).
 *
 * <p>A refusal is logged at INFO with its code alone, and a race lost on every try at WARN: neither
 * line carries a refused value or the exception's message, which can quote one. Damaged stored
 * state is logged at ERROR with its subject, detail and exception, which are value-free by
 * contract. A 500 for an exception that is no race, such as a lock wait wrapped in another
 * exception or a race after a commit, is logged at ERROR with the whole exception on purpose, as
 * {@code UnexpectedErrorHandler} does: it is a bug or an incident, and its stack trace tells which.
 */
@RestControllerAdvice
@Order(HandlerOrder.KERNEL)
class KernelErrorHandler {

    /**
     * The refusal codes that answer another status than 422, because a client must act
     * differently: sign in again (401), or give the password or stop asking (403). {@code
     * SESSION_REVOKED} stays 422 until #170 maps it with the access token's refusals. A test checks
     * that a module declares each code.
     */
    static final Map<String, HttpStatus> OVERRIDES = Map.of(
            "STEP_UP_REQUIRED", HttpStatus.FORBIDDEN,
            "NOT_YOUR_ROUTE", HttpStatus.FORBIDDEN,
            "STOP_NOT_ON_YOUR_ROUTE", HttpStatus.FORBIDDEN,
            "REFRESH_REFUSED", HttpStatus.UNAUTHORIZED);

    private static final HttpStatus REFUSED = HttpStatus.UNPROCESSABLE_CONTENT;
    private static final String CODE = "code";
    private static final String KIND = "kind";
    private static final String ID = "id";

    private static final Logger log = LoggerFactory.getLogger(KernelErrorHandler.class);

    @ExceptionHandler(RuleViolation.class)
    ResponseEntity<ProblemDetail> refused(RuleViolation violation) {
        String code = violation.error().code();
        HttpStatus status = OVERRIDES.getOrDefault(code, REFUSED);
        logRefusal(status, code);
        if (status == HttpStatus.UNAUTHORIZED) {
            return Problems.unauthorized(code);
        }
        return ResponseEntity.status(status).body(Problems.of(status, violation.error()));
    }

    @ExceptionHandler(RuleViolations.class)
    ResponseEntity<ProblemDetail> refused(RuleViolations violations) {
        String code = violations.summary().code();
        HttpStatus status = OVERRIDES.getOrDefault(code, REFUSED);
        logRefusal(status, code);
        if (status == HttpStatus.UNAUTHORIZED) {
            return Problems.unauthorized(code);
        }
        return ResponseEntity.status(status).body(Problems.of(status, violations));
    }

    @ExceptionHandler(NotFound.class)
    ResponseEntity<ProblemDetail> notFound(NotFound missing) {
        logRefusal(HttpStatus.NOT_FOUND, missing.code());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Problems.of(
                        HttpStatus.NOT_FOUND,
                        missing.code(),
                        Map.of(KIND, missing.kind(), ID, missing.id().toString())));
    }

    /**
     * Answers a lost race. The parameter is the exception thrown, never a cause Spring matched, so
     * {@link Race} reads its own type: a lock wait wrapped in another exception is no race, and
     * answers 500.
     */
    @ExceptionHandler({
        StaleVersion.class,
        PessimisticLockingFailureException.class,
        PessimisticLockException.class,
        LockTimeoutException.class
    })
    ResponseEntity<ProblemDetail> race(Exception thrown, HttpServletRequest request) {
        Race race = Race.of(thrown, Race.preconditionSent(request));
        return switch (race) {
            case NONE -> unexpected(thrown);
            case RETRY -> {
                log.atWarn().addKeyValue(CODE, race.code()).log("A call lost every try to a race or a lock wait");
                yield ResponseEntity.status(race.status())
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS))
                        .body(Problems.of(race.status(), race.code()));
            }
            case PRECONDITION_FAILED, STALE_BODY_VERSION -> {
                StaleVersion stale = (StaleVersion) thrown;
                logRefusal(race.status(), race.code());
                yield ResponseEntity.status(race.status())
                        .body(Problems.of(
                                race.status(),
                                race.code(),
                                Map.of(KIND, stale.kind(), ID, stale.id().toString())));
            }
        };
    }

    @ExceptionHandler(StoredStateInvalid.class)
    ResponseEntity<ProblemDetail> storedStateInvalid(StoredStateInvalid damaged) {
        // Its subject and detail are value-free by contract, so this line may carry them and the
        // exception; the body says nothing of the cause.
        log.atError()
                .addKeyValue("subject", damaged.subject())
                .addKeyValue("detail", damaged.detail())
                .setCause(damaged)
                .log("Stored state could not be rebuilt");
        return ResponseEntity.internalServerError()
                .body(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR));
    }

    private ResponseEntity<ProblemDetail> unexpected(Exception thrown) {
        log.error("Unhandled exception while serving a request", thrown);
        return ResponseEntity.internalServerError()
                .body(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR));
    }

    private static void logRefusal(HttpStatus status, String code) {
        log.atInfo()
                .addKeyValue("status", status.value())
                .addKeyValue(CODE, code)
                .log("Refused a call");
    }
}
