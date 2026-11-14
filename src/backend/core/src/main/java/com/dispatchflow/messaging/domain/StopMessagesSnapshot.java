package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * {@link StopMessages}' whole state: what {@code snapshot()} gives and {@code restore} takes. The
 * repository rebuilds the phone with {@code PhoneNumber.ofStored} and each text with {@link
 * MessageText#ofStored}. It holds the recipient's phone and the messages' texts, so {@link
 * #toString()} leaves them out.
 *
 * @param stop the stop the messages are about, which keys the aggregate
 * @param version the version it was loaded at
 * @param phone the phone the latest message went to; {@link RecipientPhone.None} before any
 * @param messages every message to the stop, in the order they were triggered
 */
public record StopMessagesSnapshot(StopId stop, Version version, RecipientPhone phone, List<OutgoingMessage> messages)
        implements PersonalData {

    /** Checks every part is present and copies the list. */
    public StopMessagesSnapshot {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(phone, "phone");
        messages = List.copyOf(messages);
    }

    /**
     * A form that names the stop and its messages and leaves the phone and the texts out.
     *
     * @return the snapshot without personal data
     */
    @Override
    public String toString() {
        return "StopMessagesSnapshot[stop=" + stop + ", version=" + version + ", phone="
                + (phone instanceof RecipientPhone.Known ? "redacted" : "none") + ", messages=" + messages + "]";
    }
}
