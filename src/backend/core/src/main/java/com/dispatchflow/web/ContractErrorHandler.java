package com.dispatchflow.web;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.adapter.in.web.HandlerOrder;
import com.dispatchflow.shared.adapter.in.web.ProblemItem;
import com.dispatchflow.shared.adapter.in.web.ProblemItem.Place;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.web.security.Caller;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers a request that breaks the contract, and Spring MVC's own errors, with the contract's
 * {@code Problem} (ADR-140 decisions 5 and 9). It replaces Spring Boot's handler of Spring MVC's
 * errors, which stands back once a {@code ResponseEntityExceptionHandler} bean exists, and keeps
 * its choice of status and its {@code Allow} and {@code Accept} headers: it only replaces the body.
 *
 * <ul>
 *   <li>400 {@code INVALID_REQUEST}, with every failing field or parameter of a failed validation
 *       at once, or the first problem of an unreadable body alone, or the whole request when Tomcat
 *       refuses its parameters; never with no item, and with at most its first {@link
 *       Problems#MAX_ITEMS};
 *   <li>413 {@code BODY_TOO_LARGE} for a body, an upload or a form body over its limit;
 *   <li>401 {@code ACCESS_TOKEN_REFUSED}, with exactly {@code WWW-Authenticate: Bearer}, for a 401 of
 *       Spring's own, such as a {@code ResponseStatusException};
 *   <li>404 {@code NO_SUCH_OPERATION}, 405, 406 and 415, each with a code of its own;
 *   <li>503 {@code TRY_AGAIN_LATER} for a timed-out asynchronous request, and 500 {@code
 *       UNEXPECTED_ERROR} for Spring's own server errors;
 *   <li>500 {@code UNEXPECTED_ERROR} for a status the contract doesn't list, such as the 410 of a
 *       {@code ResponseStatusException}: only a bug makes one.
 * </ul>
 *
 * <p>It runs before the kernel's handler ({@link HandlerOrder#CONTRACT}), so a converter's
 * exception that wraps a refusal still answers 400. A 400 or a 413 is logged at WARN with its codes
 * and where each failed (a cut pointer, a parameter or the whole request), the first {@link
 * Problems#MAX_ITEMS} of them and their count; a 503 at WARN; a
 * 401, 404, 405, 406 or 415 at INFO; and a 500 at ERROR. A refusal's line never carries a value nor the
 * exception's message.
 *
 * <p>A changing call it refuses before its handler runs leaves one refused audit record, with the
 * answer's code and no records, written once every transaction of the call has ended (NFR-23,
 * ADR-150 decision 4). That holds for a 4xx of the request's own dispatch whose caller the access
 * interceptor kept, under {@code /api}, whose method may change something, and whose handler
 * the call wrapper hadn't started: a 406 for an answer the handler already produced writes no
 * second record. A failed write answers 500 at ERROR, with the failure, the refusal's code and its
 * exception's type, never that exception, whose message can quote a refused value: an exception
 * thrown here would leave only Spring's WARN line, and Spring would then answer the original
 * exception itself.
 */
@RestControllerAdvice
@Order(HandlerOrder.CONTRACT)
class ContractErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ContractErrorHandler.class);

    private static final String STATUS = "status";
    private static final String CODE = "code";
    private static final String ITEMS = "items";
    private static final String ITEM_COUNT = "itemCount";
    // The exception's type, never its message: it tells apart the 400s that name no item.
    private static final String EXCEPTION = "exception";
    private static final String WHOLE_BODY = "";
    // How the log line names an item that has neither a pointer nor a parameter.
    private static final String WHOLE_REQUEST = "the whole request";
    private static final String PARAMETERS_REFUSED = "Tomcat refused the request's parameters";

    private final AuditLog auditLog;
    private final Clock clock;

    /**
     * Creates the handler.
     *
     * @param auditLog where a refused changing call's record is written
     * @param clock the time each refused record carries
     */
    ContractErrorHandler(AuditLog auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Answers a body over {@link BodySizeLimit#MAX_BODY_BYTES} that {@link BodySizeLimit} refused
     * before a converter read it.
     */
    @ExceptionHandler(BodySizeLimit.BodyTooLarge.class)
    @Nullable
    ResponseEntity<Object> bodyTooLarge(BodySizeLimit.BodyTooLarge tooLarge, WebRequest request) {
        return handleExceptionInternal(tooLarge, null, new HttpHeaders(), HttpStatus.CONTENT_TOO_LARGE, request);
    }

    /**
     * Answers Tomcat's refusal of the request's parameters when Spring MVC reads them, as it does a
     * form body's for a handler that takes a body, before any converter, so {@link BodySizeLimit}
     * never sees one. Tomcat throws it from its {@code getParameter} methods for a query or form
     * parameter that fails to decode, for too many parameters, for a form body that can't be read,
     * with 400, and for a form body over {@code server.tomcat.max-http-form-post-size}, with 413.
     * Tomcat's own 413 answers 413, and every other code answers 400 for the whole request. One
     * thrown outside Spring MVC, as by a servlet filter, reaches {@code TraceIdFilter} instead.
     *
     * <p>Its message quotes the parameter's name and value, so Spring is given a stand-in of the
     * same type with a message of its own: Spring's line for a response already committed quotes
     * the exception it is given, and the refusal's own line names only the type.
     */
    @ExceptionHandler(InvalidParameterException.class)
    @Nullable
    ResponseEntity<Object> unreadableParameters(InvalidParameterException unreadable, WebRequest request) {
        HttpStatus status = unreadable.getErrorCode() == HttpStatus.CONTENT_TOO_LARGE.value()
                ? HttpStatus.CONTENT_TOO_LARGE
                : HttpStatus.BAD_REQUEST;
        InvalidParameterException valueFree =
                new InvalidParameterException(PARAMETERS_REFUSED, unreadable.getErrorCode());
        return handleExceptionInternal(valueFree, null, new HttpHeaders(), status, request);
    }

    /** Answers a multipart body that can't be parsed; one over its limits answers 413 instead. */
    @ExceptionHandler(MultipartException.class)
    @Nullable
    ResponseEntity<Object> unreadableMultipart(MultipartException unreadable, WebRequest request) {
        return handleExceptionInternal(unreadable, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Replaces the body Spring chose with the contract's, and keeps Spring's headers and its check
     * for a response already committed. A body over its limit that a converter wrapped answers 413,
     * whatever status Spring chose, and a status the contract doesn't list answers 500. A changing
     * call refused before its handler starts first leaves its refused audit record, and answers 500
     * when that write fails, as the class Javadoc says.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception thrown, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatusCode chosen = ContractBreaks.isBodyTooLarge(thrown) ? HttpStatus.CONTENT_TOO_LARGE : status;
        Optional<String> listed = WebCodes.forStatus(chosen.value());
        if (listed.isEmpty()) {
            log.atError()
                    .addKeyValue(STATUS, chosen.value())
                    .setCause(thrown)
                    .log("Spring MVC chose a status the contract doesn't list");
            return super.handleExceptionInternal(
                    thrown,
                    Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR),
                    headers,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    request);
        }
        String code = listed.get();
        try {
            recordTheRefusal(chosen, code, request);
        } catch (RuntimeException failed) {
            // The line names the refusal's type and code, never the exception itself: Spring's
            // messages quote the values it refused, such as a field's rejected value (NFR-19).
            log.atError()
                    .addKeyValue(STATUS, chosen.value())
                    .addKeyValue(CODE, code)
                    .addKeyValue(EXCEPTION, thrown.getClass().getSimpleName())
                    .setCause(failed)
                    .log("The refused audit record of a call that breaks the contract couldn't be written");
            return super.handleExceptionInternal(
                    thrown,
                    Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR),
                    new HttpHeaders(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    request);
        }
        boolean invalid = chosen.value() == HttpStatus.BAD_REQUEST.value();
        List<ProblemItem> items = invalid ? itemsOf(thrown) : List.of();
        ProblemDetail problem = invalid ? Problems.invalidRequest(items) : Problems.of(chosen, code);
        log(thrown, chosen, code, items);
        return super.handleExceptionInternal(thrown, problem, withHeadersOf(headers, chosen), chosen, request);
    }

    /**
     * Writes the refused record of a changing call refused before its handler ran, as the class
     * Javadoc says, and nothing for any other answer.
     */
    private void recordTheRefusal(HttpStatusCode status, String code, WebRequest request) {
        if (!status.is4xxClientError() || !(request instanceof ServletWebRequest servlet)) {
            return;
        }
        HttpServletRequest http = servlet.getRequest();
        if (http.getDispatcherType() != DispatcherType.REQUEST
                || !Caller.mayChange(http)
                || CallWrapper.handlerStarted(http)) {
            return;
        }
        Optional<Caller> caller = Caller.of(http);
        if (caller.isPresent()) {
            auditLog.refused(caller.get().call(), clock.now(), List.of(), code);
        }
    }

    /** The failing items of a 400, never none: the whole request when the exception names none. */
    private static List<ProblemItem> itemsOf(Exception thrown) {
        List<ProblemItem> items = namedItemsOf(thrown);
        return items.isEmpty() ? List.of(ContractBreaks.wholeRequest()) : items;
    }

    private static List<ProblemItem> namedItemsOf(Exception thrown) {
        return switch (thrown) {
            case HttpMessageNotReadableException unreadable -> ContractBreaks.unreadable(unreadable);
            case MethodArgumentNotValidException invalid -> ContractBreaks.invalidFields(invalid.getBindingResult());
            case HandlerMethodValidationException invalid -> ContractBreaks.invalidParameters(invalid);
            case MissingRequestHeaderException missing ->
                List.of(ProblemItem.parameter(WebCodes.MISSING_PARAMETER, missing.getHeaderName(), Place.HEADER));
            case MissingRequestCookieException missing ->
                List.of(ProblemItem.parameter(WebCodes.MISSING_PARAMETER, missing.getCookieName(), Place.COOKIE));
            case MissingServletRequestParameterException missing ->
                List.of(ProblemItem.parameter(WebCodes.MISSING_PARAMETER, missing.getParameterName(), Place.QUERY));
            case TypeMismatchException mismatch -> List.of(ContractBreaks.wrongType(mismatch));
            case MissingServletRequestPartException missing ->
                List.of(ContractBreaks.missingPart(missing.getRequestPartName()));
            case MultipartException unreadable -> List.of(ProblemItem.pointer(WebCodes.UNREADABLE_BODY, WHOLE_BODY));
            default -> List.of();
        };
    }

    /** Spring's headers, with {@code Retry-After} on a 503 and the bare challenge on a 401. */
    private static HttpHeaders withHeadersOf(HttpHeaders headers, HttpStatusCode status) {
        int value = status.value();
        if (value != HttpStatus.SERVICE_UNAVAILABLE.value() && value != HttpStatus.UNAUTHORIZED.value()) {
            return headers;
        }
        HttpHeaders answered = new HttpHeaders();
        answered.addAll(headers);
        if (value == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            answered.set(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS));
        } else {
            answered.set(HttpHeaders.WWW_AUTHENTICATE, Problems.BEARER_CHALLENGE);
        }
        return answered;
    }

    private static void log(Exception thrown, HttpStatusCode status, String code, List<ProblemItem> items) {
        int value = status.value();
        if (value == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.atWarn().addKeyValue(CODE, code).log("An asynchronous request timed out");
        } else if (status.is5xxServerError()) {
            log.error("Spring MVC failed while serving a request", thrown);
        } else if (value == HttpStatus.BAD_REQUEST.value() || value == HttpStatus.CONTENT_TOO_LARGE.value()) {
            log.atWarn()
                    .addKeyValue(STATUS, value)
                    .addKeyValue(CODE, code)
                    .addKeyValue(
                            ITEMS,
                            items.stream()
                                    .limit(Problems.MAX_ITEMS)
                                    .map(ContractErrorHandler::describe)
                                    .toList())
                    .addKeyValue(ITEM_COUNT, items.size())
                    .addKeyValue(EXCEPTION, thrown.getClass().getSimpleName())
                    .log("Refused a request that breaks the contract");
        } else {
            log.atInfo()
                    .addKeyValue(STATUS, value)
                    .addKeyValue(CODE, code)
                    .log("Refused a request Spring MVC can't serve");
        }
    }

    /**
     * An item's code and where it failed, its cut pointer, its parameter or the whole request:
     * never a value.
     */
    private static String describe(ProblemItem item) {
        ProblemItem.Parameter parameter = item.parameter();
        String pointer = item.pointer();
        String place;
        if (parameter != null) {
            place = parameter.name() + " in " + parameter.in().wireName();
        } else if (pointer != null) {
            place = pointer;
        } else {
            place = WHOLE_REQUEST;
        }
        return item.code() + " at " + place;
    }
}
