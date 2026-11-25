package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.Problems;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Every code {@code web} answers of its own, as the contract's {@code Problem} lists them, the
 * codes of a 400's items included. {@code NOT_FOUND} and {@code STALE_VERSION} are not among them:
 * they are the kernel's {@code NotFound.CODE} and {@code StaleVersion.CODE}. A test checks that no
 * module declares one of these, so a client never reads one code two ways.
 *
 * <p>{@link #forStatus} is the contract's table of the statuses {@code web} answers of its own, so
 * the contract's handler and the error endpoint never answer one status two ways.
 */
final class WebCodes {

    /** 400: the request breaks the contract. */
    static final String INVALID_REQUEST = Problems.INVALID_REQUEST;

    /** 404: no operation has the request's path. */
    static final String NO_SUCH_OPERATION = "NO_SUCH_OPERATION";

    /** 405, with {@code Allow}. */
    static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

    /** 406. */
    static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";

    /** 415, with {@code Accept}. */
    static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";

    /** 413: the body is over its operation's size limit. */
    static final String BODY_TOO_LARGE = "BODY_TOO_LARGE";

    /** 503, with {@code Retry-After}: the call lost every try to a race or a lock wait. */
    static final String TRY_AGAIN_LATER = "TRY_AGAIN_LATER";

    /** 500: the server failed. */
    static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";

    /** A 400 item: the body is no JSON, or is missing. */
    static final String UNREADABLE_BODY = "UNREADABLE_BODY";

    /** A 400 item: the body has a property its schema doesn't declare. */
    static final String UNKNOWN_PROPERTY = "UNKNOWN_PROPERTY";

    /** A 400 item: the body has a key twice in one object. */
    static final String DUPLICATE_PROPERTY = "DUPLICATE_PROPERTY";

    /** A 400 item: a value of the wrong type, or one that doesn't parse as its type. */
    static final String WRONG_TYPE = "WRONG_TYPE";

    /** A 400 item: a required header, query parameter or cookie is missing. */
    static final String MISSING_PARAMETER = "MISSING_PARAMETER";

    /** A 400 item: a required value is missing or empty. */
    static final String REQUIRED = "REQUIRED";

    /** A 400 item: a text or a list is too short or too long. */
    static final String WRONG_SIZE = "WRONG_SIZE";

    /** A 400 item: a number is below its minimum. */
    static final String TOO_SMALL = "TOO_SMALL";

    /** A 400 item: a number is above its maximum. */
    static final String TOO_LARGE = "TOO_LARGE";

    /** A 400 item: a text doesn't match its pattern or format. */
    static final String WRONG_FORMAT = "WRONG_FORMAT";

    /** A 400 item: any other refused value. */
    static final String INVALID_VALUE = "INVALID_VALUE";

    /** Every code above. */
    static final Set<String> ALL = Set.of(
            INVALID_REQUEST,
            NO_SUCH_OPERATION,
            METHOD_NOT_ALLOWED,
            NOT_ACCEPTABLE,
            UNSUPPORTED_MEDIA_TYPE,
            BODY_TOO_LARGE,
            TRY_AGAIN_LATER,
            UNEXPECTED_ERROR,
            UNREADABLE_BODY,
            UNKNOWN_PROPERTY,
            DUPLICATE_PROPERTY,
            WRONG_TYPE,
            MISSING_PARAMETER,
            REQUIRED,
            WRONG_SIZE,
            TOO_SMALL,
            TOO_LARGE,
            WRONG_FORMAT,
            INVALID_VALUE);

    private static final Map<Integer, String> CODE_BY_STATUS = Map.of(
            HttpStatus.BAD_REQUEST.value(), INVALID_REQUEST,
            HttpStatus.NOT_FOUND.value(), NO_SUCH_OPERATION,
            HttpStatus.METHOD_NOT_ALLOWED.value(), METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE.value(), NOT_ACCEPTABLE,
            HttpStatus.CONTENT_TOO_LARGE.value(), BODY_TOO_LARGE,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), UNSUPPORTED_MEDIA_TYPE,
            HttpStatus.INTERNAL_SERVER_ERROR.value(), UNEXPECTED_ERROR,
            HttpStatus.SERVICE_UNAVAILABLE.value(), TRY_AGAIN_LATER);

    private WebCodes() {}

    /**
     * The code {@code web} answers a status of its own with, as the contract's {@code Problem}
     * lists them.
     *
     * @param status the HTTP status
     * @return its code, or empty for a status the contract doesn't list for {@code web}, which
     *     answers 500 instead: only a bug produces one
     */
    static Optional<String> forStatus(int status) {
        return Optional.ofNullable(CODE_BY_STATUS.get(status));
    }
}
