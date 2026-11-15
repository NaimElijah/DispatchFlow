package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.message.StopMessages.ProviderRepeats;
import com.dispatchflow.shared.PhoneNumber;

/**
 * The SMS provider: the Israeli provider's adapter in production, and a fake in every other
 * environment (Q-96, NFR-206, TS §6.4). Its inbound callbacks are verified by the adapter (NFR-192).
 *
 * <p>Built in M12.
 */
public interface SmsGateway {

    /**
     * Sends one message, giving the provider DispatchFlow's reference so it can drop a repeat
     * (NFR-198). It is called outside any transaction (TS §6.2), and never throws for the provider's
     * answer: a timeout or a broken call is {@link SendAnswer.Unknown}.
     *
     * <p>Built in M12.
     *
     * @param message DispatchFlow's reference for the message
     * @param to the phone it goes to
     * @param text the message's text, one SMS of at most 70 characters (FR-382)
     * @return what the provider answered
     */
    SendAnswer send(OutgoingMessageId message, PhoneNumber to, MessageText text);

    /**
     * Whether the configured provider drops a repeated send by DispatchFlow's reference, which
     * decides whether a send of unknown outcome is retried (NFR-198).
     *
     * <p>Built in M12.
     *
     * @return the provider's behaviour
     */
    ProviderRepeats repeats();
}
