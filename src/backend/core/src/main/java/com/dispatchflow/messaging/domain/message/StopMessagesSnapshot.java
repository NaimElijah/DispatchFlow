package com.dispatchflow.messaging.domain.message;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * {@link StopMessages}' whole state: what {@code snapshot()} gives and {@code restore} takes. The
 * repository rebuilds each message's phone with {@code PhoneNumber.ofStored} and its text with
 * {@link MessageText#ofStored}. Each message holds the recipient's phone and its text, so {@link
 * #toString()} leaves them out.
 *
 * @param stop the stop the messages are about, which keys the aggregate
 * @param version the version it was loaded at
 * @param messages every message to the stop, each with the phone it goes to, in the order they were
 *     triggered (ADR-117)
 */
public record StopMessagesSnapshot(StopId stop, Version version, List<OutgoingMessage> messages)
        implements PersonalData {

    /** Checks every part is present and copies the list. */
    public StopMessagesSnapshot {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(version, "version");
        messages = List.copyOf(messages);
    }

    /**
     * A form that names the stop and its messages and leaves the phones and the texts out.
     *
     * @return the snapshot without personal data
     */
    @Override
    public String toString() {
        return "StopMessagesSnapshot[stop=" + stop + ", version=" + version + ", messages=" + messages + "]";
    }
}
