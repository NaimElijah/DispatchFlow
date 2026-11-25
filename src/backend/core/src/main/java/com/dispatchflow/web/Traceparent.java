package com.dispatchflow.web;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the trace-id of a W3C Trace Context {@code traceparent} header (ADR-140 decision 8). Only
 * the trace-id is kept: the parent-id and the flags are the caller's own, and the core makes its
 * own parent-id for the optimizer call. A request without a valid one gets a fresh ID from {@code
 * TraceIds}.
 */
final class Traceparent {

    // Version 00's layout: version, trace-id, parent-id and flags, in lower-case hex.
    private static final int VERSION_00_LENGTH = 55;
    private static final Pattern VERSION_00_LAYOUT =
            Pattern.compile("([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}");
    private static final String VERSION_00 = "00";
    // W3C Trace Context reserves version ff as invalid.
    private static final String INVALID_VERSION = "ff";
    private static final char FIELD_SEPARATOR = '-';
    private static final char ZERO = '0';

    private Traceparent() {}

    /**
     * The trace-id of a valid {@code traceparent}: exactly one header value, lower-case hex, a
     * version other than {@code ff}, exactly 55 characters for version 00, and for a later version
     * at least 55, read in version 00's layout, with a {@code -} after its flags when longer; and
     * neither the trace-id nor the parent-id all zeros.
     *
     * @param headerValues every value the request sent for the header, possibly none
     * @return the 32-digit trace-id, or empty when the header is missing or invalid
     */
    static Optional<String> traceIdOf(List<String> headerValues) {
        if (headerValues.size() != 1) {
            return Optional.empty();
        }
        String value = headerValues.getFirst();
        if (value.length() < VERSION_00_LENGTH || !fitsItsVersion(value)) {
            return Optional.empty();
        }
        Matcher fields = VERSION_00_LAYOUT.matcher(value.substring(0, VERSION_00_LENGTH));
        if (!fields.matches() || fields.group(1).equals(INVALID_VERSION)) {
            return Optional.empty();
        }
        String traceId = fields.group(2);
        if (isAllZeros(traceId) || isAllZeros(fields.group(3))) {
            return Optional.empty();
        }
        return Optional.of(traceId);
    }

    private static boolean fitsItsVersion(String value) {
        if (value.startsWith(VERSION_00)) {
            return value.length() == VERSION_00_LENGTH;
        }
        return value.length() == VERSION_00_LENGTH || value.charAt(VERSION_00_LENGTH) == FIELD_SEPARATOR;
    }

    private static boolean isAllZeros(String hex) {
        return hex.chars().allMatch(digit -> digit == ZERO);
    }
}
