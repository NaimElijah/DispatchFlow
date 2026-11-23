package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.InboundReply;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the replies in {@code messaging.inbound_reply}, one row per provider identifier. */
@Repository
@Transactional
class JpaReplyLog implements ReplyLog {

    private final StoredReplies replies;

    /**
     * Creates the adapter.
     *
     * @param replies the Spring Data interface
     */
    JpaReplyLog(StoredReplies replies) {
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Override
    public boolean append(InboundReply reply) {
        return replies.append(reply.providerId(), reply.from().e164(), reply.text(), reply.receivedAt()) == 1;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return replies.deleteReceivedBefore(cutoff);
    }
}
