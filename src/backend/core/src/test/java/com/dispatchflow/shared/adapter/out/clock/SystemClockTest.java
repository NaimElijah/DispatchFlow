package com.dispatchflow.shared.adapter.out.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemClockTest {

    /** 22:30 UTC on 14 March, which is already 00:30 on 15 March in Israel (UTC+2 in winter). */
    private static final Instant LATE_EVENING_UTC = Instant.parse("2026-03-14T22:30:00Z");

    private static final LocalDate ISRAELI_DATE = LocalDate.of(2026, 3, 15);

    @Test
    void GivenAnInstantBeforeMidnightUtcButAfterItInIsrael_WhenTodayIsAsked_ThenTheIsraeliDate() {
        SystemClock clock = new SystemClock(Clock.fixed(LATE_EVENING_UTC, ZoneOffset.UTC));

        assertThat(clock.today()).isEqualTo(ISRAELI_DATE);
    }

    @Test
    void GivenAClockWithNanoseconds_WhenNowIsRead_ThenItIsTruncatedToTheMicrosecond() {
        Instant withNanos = Instant.parse("2026-03-14T10:00:00.123456789Z");
        SystemClock clock = new SystemClock(Clock.fixed(withNanos, ZoneOffset.UTC));

        assertThat(clock.now()).isEqualTo(Instant.parse("2026-03-14T10:00:00.123456Z"));
    }

    @Test
    void GivenAClockInTokyo_WhenTodayIsAsked_ThenTheIsraeliDateNotTokyos() {
        // 23:30 UTC is 08:30 on 16 March in Tokyo, but 01:30 on 15 March in Israel.
        Instant instant = Instant.parse("2026-03-14T23:30:00Z");
        SystemClock clock = new SystemClock(Clock.fixed(instant, ZoneId.of("Asia/Tokyo")));

        assertThat(clock.today()).isEqualTo(ISRAELI_DATE);
    }
}
