package com.dispatchflow.shared.adapter.in.web;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RuleViolations;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Builds every error body the core answers, as the contract's {@code Problem} (RFC 9457, ADR-043,
 * ADR-140 decision 5): {@code type} {@code about:blank}, the status's own phrase as {@code title},
 * a {@code code}, its {@code params} and the request's {@code traceId}, and {@code errors} on a 400
 * and on a 422 that lists several. {@code web}'s handlers and every module's {@code adapter.in.web}
 * handler use it, so no body is built twice. It never sets {@code detail}: a client shows its own
 * text for the {@code code}, and nothing from inside the server reaches the body.
 *
 * <p>The trace ID comes from the MDC entry {@link #TRACE_ID} that {@code web}'s trace filter sets
 * for the request (ADR-140 decision 8), never from the call's holders. A problem made where the MDC
 * holds none still carries one, fresh from {@link TraceIds}, because the contract requires the
 * member; that is logged at WARN, since every request is traced and only a bug skips the filter.
 */
public final class Problems {

    /** The MDC key that carries the request's trace ID, and the problem's member of that name. */
    public static final String TRACE_ID = "traceId";

    /** The longest pointer a problem's item carries, in code points, as the contract caps it. */
    public static final int MAX_POINTER_LENGTH = 128;

    /** The one challenge every 401 carries, with no parameters (ADR-140 decision 7). */
    public static final String BEARER_CHALLENGE = "Bearer";

    /**
     * The most items a 400 lists, in the order validation found them, which needn't be the body's
     * order (decided on 25 November 2026, ADR-142). A crafted body can fail validation a great many times, and the answer and its log
     * line stay small. A 422's refusals are not capped: the domain bounds them, as an import's rows.
     */
    public static final int MAX_ITEMS = 100;

    /** The code of a request that breaks the contract, answered with 400 and its items. */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    // Spring Framework 7 leaves a ProblemDetail's type unset, which the body would then omit, but
    // the contract requires the member.
    private static final URI ABOUT_BLANK = URI.create("about:blank");
    private static final String CODE = "code";
    private static final String PARAMS = "params";
    private static final String ERRORS = "errors";

    private static final Logger log = LoggerFactory.getLogger(Problems.class);

    private Problems() {}

    /**
     * A problem with a code that needs no params.
     *
     * @param status the answer's status
     * @param code the stable code
     * @return the problem, with empty {@code params}
     */
    public static ProblemDetail of(HttpStatusCode status, String code) {
        return of(status, code, Map.of());
    }

    /**
     * A problem with a code and its params.
     *
     * @param status the answer's status
     * @param code the stable code
     * @param params the values the code's text needs: never personal data, a secret or a token. A
     *     refusal's params can name the value it refused, such as {@code DATE_IN_PAST}'s date; a
     *     400 item's never do
     * @return the problem
     */
    public static ProblemDetail of(HttpStatusCode status, String code, Map<String, ?> params) {
        Objects.requireNonNull(code, "code");
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(ABOUT_BLANK);
        problem.setProperty(CODE, code);
        problem.setProperty(PARAMS, new LinkedHashMap<String, Object>(params));
        problem.setProperty(TRACE_ID, traceId());
        return problem;
    }

    /**
     * A problem for a refused rule, with the error's code and its components as params.
     *
     * @param status the answer's status, 422 unless a code overrides it
     * @param error the refusal
     * @return the problem
     * @throws IllegalStateException when the error's components have no mapping to params, which a
     *     test over every production error rules out
     */
    public static ProblemDetail of(HttpStatusCode status, DomainError error) {
        return of(status, error.code(), ErrorParams.of(error));
    }

    /**
     * A problem for refusals listed at once: the summary's code and params, and every refusal in
     * {@code errors}, each with its own code and params.
     *
     * @param status the answer's status, 422 unless the summary's code overrides it
     * @param violations the refusals
     * @return the problem
     * @throws IllegalStateException as {@link #of(HttpStatusCode, DomainError)} does
     */
    public static ProblemDetail of(HttpStatusCode status, RuleViolations violations) {
        ProblemDetail problem = of(status, violations.summary());
        problem.setProperty(
                ERRORS,
                violations.errors().stream()
                        .map(error -> ProblemItem.refusal(error).asMap())
                        .toList());
        return problem;
    }

    /**
     * A 400 for a request that breaks the contract, listing its first {@link #MAX_ITEMS} failing
     * items.
     *
     * @param items the failing items, in the order found
     * @return the problem, with code {@link #INVALID_REQUEST}
     * @throws IllegalArgumentException when {@code items} is empty, which the contract forbids
     */
    public static ProblemDetail invalidRequest(List<ProblemItem> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("An invalid request names at least one failing item");
        }
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, INVALID_REQUEST);
        problem.setProperty(
                ERRORS, items.stream().limit(MAX_ITEMS).map(ProblemItem::asMap).toList());
        return problem;
    }

    /**
     * A 401 with the bare {@code WWW-Authenticate: Bearer} challenge and empty params, so every
     * refusal of a token or a refresh looks the same (NFR-92, ADR-140 decision 7).
     *
     * @param code the one code the refusal answers, such as {@code REFRESH_REFUSED}
     * @return the answer, its header and its body
     */
    public static ResponseEntity<ProblemDetail> unauthorized(String code) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
                .body(of(HttpStatus.UNAUTHORIZED, code));
    }

    private static String traceId() {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null) {
            return traceId;
        }
        String fresh = TraceIds.fresh();
        log.atWarn().addKeyValue(TRACE_ID, fresh).log("A problem was made outside a traced request");
        return fresh;
    }
}
