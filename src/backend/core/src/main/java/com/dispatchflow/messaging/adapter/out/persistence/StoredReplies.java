package com.dispatchflow.messaging.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaReplyLog}: two native statements, no lookup. */
interface StoredReplies extends Repository<StoredReply, String> {

    /**
     * Keeps a reply unless one with its provider identifier is kept.
     *
     * @param providerId the provider's identifier for the reply
     * @param phone the phone it came from, in E.164 form
     * @param text the reply's text
     * @param receivedAt when it was received
     * @return 1 when it was kept now, 0 when the provider sent it before
     */
    @Modifying
    @Query(value = """
            INSERT INTO messaging.inbound_reply (provider_id, phone, text, received_at)
            VALUES (:providerId, :phone, :text, :receivedAt)
            ON CONFLICT (provider_id) DO NOTHING
            """, nativeQuery = true)
    int append(String providerId, String phone, String text, Instant receivedAt);

    /**
     * Deletes every reply received before a cutoff.
     *
     * @param cutoff the first receipt time kept
     * @return how many replies were deleted
     */
    @Modifying
    @Query(value = "DELETE FROM messaging.inbound_reply WHERE received_at < :cutoff", nativeQuery = true)
    int deleteReceivedBefore(Instant cutoff);
}
