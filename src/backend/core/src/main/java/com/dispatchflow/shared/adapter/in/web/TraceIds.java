package com.dispatchflow.shared.adapter.in.web;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Makes a fresh W3C trace-id, for a request that brought no valid {@code traceparent} and for a
 * problem made outside a traced request (ADR-140 decision 8). It sits beside {@link Problems}, so
 * {@code web}'s trace filter and the factory make the same kind of ID.
 */
public final class TraceIds {

    // A W3C trace-id is 16 bytes, written as 32 lower-case hex digits.
    private static final int TRACE_ID_BYTES = 16;
    // Strong randomness: a client sees the ID in every problem body, so it must not let one guess
    // another request's ID.
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat LOWER_CASE_HEX = HexFormat.of();

    private TraceIds() {}

    /**
     * A random 128-bit trace-id, never all zeros, which W3C Trace Context and {@code
     * audit.domain.TraceId} refuse.
     *
     * @return 32 lower-case hex digits
     */
    public static String fresh() {
        byte[] traceId = new byte[TRACE_ID_BYTES];
        do {
            RANDOM.nextBytes(traceId);
        } while (isAllZeros(traceId));
        return LOWER_CASE_HEX.formatHex(traceId);
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
