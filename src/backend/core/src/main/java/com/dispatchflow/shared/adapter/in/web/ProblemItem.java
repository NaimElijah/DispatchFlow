package com.dispatchflow.shared.adapter.in.web;

import com.dispatchflow.shared.DomainError;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a problem's {@code errors} (the contract's {@code ProblemItem}): a failing item of a
 * 400, which says where it failed, by a pointer into the body or a parameter's name and place, and
 * never the value; or one refusal of a 422 that lists several, with its own code and params.
 *
 * @param code the item's stable code, such as {@code UNKNOWN_PROPERTY}
 * @param pointer an RFC 6901 JSON Pointer into the body, or {@code null} when the item names a
 *     parameter or none; one longer than {@link Problems#MAX_POINTER_LENGTH} code points is cut to
 *     its deepest ancestor within that length
 * @param parameter the failing parameter, or {@code null} when the item names a pointer or none
 * @param params the values the code's text needs, never the refused value
 */
public record ProblemItem(
        String code, @Nullable String pointer, @Nullable Parameter parameter, Map<String, Object> params) {

    private static final String CODE = "code";
    private static final String POINTER = "pointer";
    private static final String PARAMETER = "parameter";
    private static final String PARAMS = "params";
    private static final String NAME = "name";
    private static final String IN = "in";
    private static final String WHOLE_BODY = "";
    private static final char POINTER_STEP = '/';

    /**
     * Cuts a long pointer, and copies the params, keeping their order.
     *
     * @throws IllegalArgumentException when both a pointer and a parameter are given: an item fails
     *     at one place
     */
    public ProblemItem {
        Objects.requireNonNull(code, "code");
        if (pointer != null && parameter != null) {
            throw new IllegalArgumentException("An item fails at a pointer or at a parameter, never both");
        }
        pointer = pointer == null ? null : cut(pointer);
        params = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(params, "params")));
    }

    /**
     * An item that fails at a place in the body, with no params.
     *
     * @param code the item's code
     * @param jsonPointer the RFC 6901 pointer, {@code ""} for the whole body; one longer than {@link
     *     Problems#MAX_POINTER_LENGTH} code points is cut to its deepest ancestor within that length
     * @return the item
     */
    public static ProblemItem pointer(String code, String jsonPointer) {
        return pointer(code, jsonPointer, Map.of());
    }

    /**
     * An item that fails at a place in the body.
     *
     * @param code the item's code
     * @param jsonPointer the RFC 6901 pointer, {@code ""} for the whole body; one longer than {@link
     *     Problems#MAX_POINTER_LENGTH} code points is cut to its deepest ancestor within that length
     * @param params the values the code's text needs, such as a constraint's {@code min}
     * @return the item
     */
    public static ProblemItem pointer(String code, String jsonPointer, Map<String, ?> params) {
        Objects.requireNonNull(jsonPointer, "jsonPointer");
        return new ProblemItem(code, jsonPointer, null, new LinkedHashMap<String, Object>(params));
    }

    /**
     * An item that fails at a parameter, with no params.
     *
     * @param code the item's code
     * @param name the parameter's name, as the contract declares it
     * @param in where the parameter is
     * @return the item
     */
    public static ProblemItem parameter(String code, String name, Place in) {
        return parameter(code, name, in, Map.of());
    }

    /**
     * An item that fails at a parameter.
     *
     * @param code the item's code
     * @param name the parameter's name, as the contract declares it
     * @param in where the parameter is
     * @param params the values the code's text needs, such as a constraint's {@code min}
     * @return the item
     */
    public static ProblemItem parameter(String code, String name, Place in, Map<String, ?> params) {
        return new ProblemItem(code, null, new Parameter(name, in), new LinkedHashMap<String, Object>(params));
    }

    /**
     * An item of a 400 for the request as a whole, when no field of the body and no parameter can
     * be named: it has neither a pointer, whose empty value would mean the whole body, nor a
     * parameter.
     *
     * @param code the item's code, such as {@code INVALID_VALUE}
     * @return the item, with empty params
     */
    public static ProblemItem request(String code) {
        return new ProblemItem(code, null, null, Map.of());
    }

    /**
     * One refusal of a 422 that lists several.
     *
     * @param error the refusal
     * @return the item, with the error's code and params
     * @throws IllegalStateException when the error's components have no mapping, as {@link
     *     Problems#of(org.springframework.http.HttpStatusCode, DomainError)} says
     */
    public static ProblemItem refusal(DomainError error) {
        return new ProblemItem(error.code(), null, null, ErrorParams.of(error));
    }

    /**
     * The item as the body carries it, with no member for an absent pointer or parameter.
     *
     * @return the item's members, in the contract's order
     */
    public Map<String, Object> asMap() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put(CODE, code);
        if (pointer != null) {
            item.put(POINTER, pointer);
        }
        if (parameter != null) {
            Map<String, Object> place = new LinkedHashMap<>();
            place.put(NAME, parameter.name());
            place.put(IN, parameter.in().wireName());
            item.put(PARAMETER, place);
        }
        item.put(PARAMS, params);
        return item;
    }

    /**
     * The pointer, or, when it is longer than the limit, the part before the last {@code /} that
     * leaves at most {@link Problems#MAX_POINTER_LENGTH} code points: its deepest whole-token
     * ancestor, the whole body at worst. A cut at the limit itself could end inside a token or a
     * {@code ~} escape, which would point somewhere else or nowhere.
     */
    private static String cut(String pointer) {
        if (pointer.codePointCount(0, pointer.length()) <= Problems.MAX_POINTER_LENGTH) {
            return pointer;
        }
        int firstPastTheLimit = pointer.offsetByCodePoints(0, Problems.MAX_POINTER_LENGTH);
        int step = pointer.lastIndexOf(POINTER_STEP, firstPastTheLimit);
        return step <= 0 ? WHOLE_BODY : pointer.substring(0, step);
    }

    /**
     * A failing parameter.
     *
     * @param name the parameter's name, as the contract declares it
     * @param in where the parameter is
     */
    public record Parameter(String name, Place in) {

        /** Checks both parts are present. */
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(in, "in");
        }
    }

    /** Where a parameter is, as OpenAPI's {@code in} names it. */
    public enum Place {
        /** A path parameter. */
        PATH,
        /** A query parameter. */
        QUERY,
        /** A header, such as {@code If-Match}. */
        HEADER,
        /** A cookie. */
        COOKIE;

        /**
         * The name the contract gives this place.
         *
         * @return the constant's name in lower case, such as {@code header}
         */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
