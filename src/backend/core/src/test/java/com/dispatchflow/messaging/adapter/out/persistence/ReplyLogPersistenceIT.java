package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.Transactions;
import com.dispatchflow.messaging.application.port.out.InboundReply;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ReplyLog} against a real PostgreSQL (ADR-127 decision 6): a reply is kept once by its
 * provider identifier (NFR-199), and the ones received before a cutoff are deleted (NFR-184).
 *
 * <p>The database is shared and never cleaned, so each test uses its own provider identifiers, and
 * only the deletion test receives replies before 2030.
 */
@IntegrationTest
class ReplyLogPersistenceIT {

    private static final Instant RECEIVED = Instant.parse("2030-01-01T08:00:00Z");
    private static final PhoneNumber PHONE = PhoneNumber.ofStored("+972523456789");

    @Autowired
    private ReplyLog replyLog;

    @Autowired
    private Transactions transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void GivenANewReply_WhenAppended_ThenItIsKeptWithEveryPart() {
        InboundReply reply = new InboundReply("reply-log-1", PHONE, "8 thanks", RECEIVED);

        boolean appended = transactions.inTransaction(() -> replyLog.append(reply));

        assertThat(appended).isTrue();
        assertThat(stored("reply-log-1"))
                .isEqualTo(Map.of("phone", "+972523456789", "text", "8 thanks", "received_at", RECEIVED));
    }

    @Test
    void GivenAKeptReply_WhenTheProviderSendsItAgain_ThenItIsNotAppendedAndTheFirstStaysAsItWas() {
        transactions.inTransaction(() -> replyLog.append(new InboundReply("reply-log-2", PHONE, "first", RECEIVED)));

        boolean again = transactions.inTransaction(
                () -> replyLog.append(new InboundReply("reply-log-2", PHONE, "second", RECEIVED.plusSeconds(60))));

        assertThat(again).isFalse();
        assertThat(stored("reply-log-2")).containsEntry("text", "first").containsEntry("received_at", RECEIVED);
    }

    @Test
    void GivenRepliesAroundACutoff_WhenOlderOnesAreDeleted_ThenOnlyThoseReceivedBeforeItGo() {
        // In 2020, before every other test's replies.
        Instant cutoff = Instant.parse("2020-04-09T07:00:00Z");
        transactions.inTransaction(() -> {
            replyLog.append(new InboundReply("reply-log-3a", PHONE, "before", cutoff.minusSeconds(1)));
            replyLog.append(new InboundReply("reply-log-3b", PHONE, "at", cutoff));
            replyLog.append(new InboundReply("reply-log-3c", PHONE, "after", cutoff.plusSeconds(1)));
        });

        int beforeAny = transactions.inTransaction(() -> replyLog.deleteOlderThan(cutoff.minusSeconds(1)));
        int deleted = transactions.inTransaction(() -> replyLog.deleteOlderThan(cutoff));

        assertThat(List.of(beforeAny, deleted)).containsExactly(0, 1);
        assertThat(jdbc.sql("SELECT provider_id FROM messaging.inbound_reply WHERE provider_id LIKE 'reply-log-3%'")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder("reply-log-3b", "reply-log-3c");
    }

    private Map<String, Object> stored(String providerId) {
        return jdbc.sql("SELECT phone, text, received_at FROM messaging.inbound_reply WHERE provider_id = ?")
                .param(providerId)
                .query((row, number) -> Map.<String, Object>of(
                        "phone", row.getString("phone"),
                        "text", row.getString("text"),
                        "received_at",
                                row.getObject("received_at", OffsetDateTime.class)
                                        .toInstant()))
                .single();
    }
}
