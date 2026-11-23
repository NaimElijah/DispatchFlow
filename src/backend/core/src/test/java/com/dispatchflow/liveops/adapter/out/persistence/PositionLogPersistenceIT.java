package com.dispatchflow.liveops.adapter.out.persistence;

import static com.dispatchflow.liveops.domain.LiveOpsFixtures.DRIVER_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.liveops.application.port.out.KeptPosition;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.liveops.domain.delivery.RetentionPeriods;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.RouteId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PositionLog} against a real PostgreSQL (ADR-126, ADR-127 decision 6): the positions kept,
 * a route's last two, and their deletion after 7 days.
 *
 * <p>The database is shared and never cleaned, and a deletion reads every position. So test {@code
 * n} keeps positions for its own routes ({@code route(n)}, {@code route(100 + n)}, …), and the
 * deletion test works in 2020, years before every other position this class keeps.
 */
@IntegrationTest
class PositionLogPersistenceIT {

    private static final long KEY_PREFIX = 0x0190a5e2_706f_7301L;
    private static final Instant T = Instant.parse("2031-06-01T07:00:00Z");
    private static final GeoPoint TEL_AVIV = GeoPoint.of(32.0853, 34.7818);
    private static final GeoPoint JAFFA = GeoPoint.of(32.0504, 34.7522);
    private static final GeoPoint HOLON = GeoPoint.of(32.0158, 34.7874);

    @Autowired
    private PositionLog log;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void GivenThreePositionsAppendedOutOfOrder_WhenTheRoutesLastTwoAreRead_ThenTheyAreTheLatestTwoLatestLast() {
        int n = 1;
        KeptPosition first = kept(n, TEL_AVIV, T);
        KeptPosition second = kept(n, JAFFA, T.plus(Duration.ofMinutes(1)));
        KeptPosition third = kept(n, HOLON, T.plus(Duration.ofMinutes(2)));
        transactions.inTransaction(() -> {
            log.append(List.of(first, third));
            log.append(List.of(second, kept(100 + n, TEL_AVIV, T.plus(Duration.ofMinutes(5)))));
        });

        assertThat(transactions.inTransaction(() -> log.lastTwo(route(n)))).containsExactly(second, third);
    }

    @Test
    void GivenTwoPositionsRecordedAtOneTime_WhenTheLastTwoAreRead_ThenTheLaterReceiptComesLast() {
        int n = 2;
        KeptPosition receivedFirst = new KeptPosition(route(n), DRIVER_A, TEL_AVIV, T, T.plusSeconds(10));
        KeptPosition receivedLater = new KeptPosition(route(n), DRIVER_A, JAFFA, T, T.plusSeconds(20));
        transactions.inTransaction(() -> log.append(List.of(receivedLater, receivedFirst)));

        assertThat(transactions.inTransaction(() -> log.lastTwo(route(n))))
                .containsExactly(receivedFirst, receivedLater);
    }

    @Test
    void GivenOnePositionKept_WhenTheLastTwoAreRead_ThenThereIsOne() {
        int n = 3;
        KeptPosition only = kept(n, TEL_AVIV, T);
        transactions.inTransaction(() -> log.append(List.of(only)));

        assertThat(transactions.inTransaction(() -> log.lastTwo(route(n)))).containsExactly(only);
    }

    @Test
    void GivenNoPositionsAppended_WhenTheLastTwoAreRead_ThenThereAreNone() {
        int n = 4;
        transactions.inTransaction(() -> log.append(List.of()));

        assertThat(transactions.inTransaction(() -> log.lastTwo(route(n)))).isEmpty();
        assertThat(storedPositions(n)).isZero();
    }

    @Test
    void GivenPositionsAroundTheCutoff_WhenOlderOnesAreDeleted_ThenThoseReceivedAtOrBeforeItGo() {
        int n = 5;
        Instant now = Instant.parse("2020-03-30T07:00:00Z");
        // Seven Israel days before, across the spring clock change: an hour less than 7 × 24 hours.
        Instant cutoff = RetentionPeriods.positionCutoff(now);
        transactions.inTransaction(() -> log.append(
                List.of(received(n, cutoff.minusSeconds(1)), received(n, cutoff), received(n, cutoff.plusSeconds(1)))));

        int deleted = transactions.inTransaction(() -> log.deleteOlderThan(cutoff));

        assertThat(cutoff).isEqualTo(now.minus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        assertThat(deleted).isEqualTo(2);
        assertThat(transactions.inTransaction(() -> log.lastTwo(route(n))))
                .extracting(KeptPosition::receivedAt)
                .containsExactly(cutoff.plusSeconds(1));
    }

    private static KeptPosition kept(int r, GeoPoint at, Instant recordedAt) {
        return new KeptPosition(route(r), DRIVER_A, at, recordedAt, recordedAt.plusSeconds(3));
    }

    private static KeptPosition received(int r, Instant receivedAt) {
        return new KeptPosition(route(r), DRIVER_A, TEL_AVIV, receivedAt.minusSeconds(3), receivedAt);
    }

    private static RouteId route(int r) {
        return RouteId.of(new UUID(KEY_PREFIX, r));
    }

    private long storedPositions(int r) {
        return jdbc.sql("SELECT count(*) FROM liveops.kept_position WHERE route = ?")
                .param(route(r).value())
                .query(Long.class)
                .single();
    }
}
