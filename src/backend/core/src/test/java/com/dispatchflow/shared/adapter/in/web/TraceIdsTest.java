package com.dispatchflow.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.TraceId;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class TraceIdsTest {

    @Test
    void GivenNothingToRead_WhenAFreshTraceIdIsMade_ThenAuditAcceptsIt() {
        String fresh = TraceIds.fresh();

        assertThat(fresh).matches("[0-9a-f]{32}");
        assertThat(TraceId.of(fresh).value()).isEqualTo(fresh);
    }

    @Test
    void GivenTwoFreshTraceIds_WhenCompared_ThenTheyDiffer() {
        assertThat(TraceIds.fresh()).isNotEqualTo(TraceIds.fresh());
    }

    @Test
    void GivenASourceThatYieldsAllZerosFirst_WhenAFreshTraceIdIsMade_ThenItDrawsAgainAndIsNeverAllZeros() {
        AtomicInteger draws = new AtomicInteger();
        // Each source throws on a draw it should never be asked for, so a zero check that misses a
        // byte fails the test instead of drawing the same bytes forever.
        Consumer<byte[]> zerosThenOnes = bytes -> {
            int draw = draws.incrementAndGet();
            if (draw > 2) {
                throw new AssertionError("a draw of ones was refused as all zeros");
            }
            Arrays.fill(bytes, (byte) (draw == 1 ? 0 : 1));
        };

        String fresh = TraceIds.fresh(zerosThenOnes);

        assertThat(fresh).isEqualTo("01".repeat(16));
        assertThat(draws).hasValue(2);
    }

    @Test
    void GivenADrawOfZerosButAOneInItsLastByte_WhenAFreshTraceIdIsMade_ThenItIsTakenAtOnce() {
        AtomicInteger draws = new AtomicInteger();
        Consumer<byte[]> lastByteSet = bytes -> {
            if (draws.incrementAndGet() > 1) {
                throw new AssertionError("a draw with its last byte set was refused as all zeros");
            }
            Arrays.fill(bytes, (byte) 0);
            bytes[bytes.length - 1] = 1;
        };

        String fresh = TraceIds.fresh(lastByteSet);

        assertThat(fresh).isEqualTo("00".repeat(15) + "01");
        assertThat(draws).hasValue(1);
    }
}
