package com.dispatchflow.messaging.domain;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one check of {@link StopMessages}' invariants at rest. {@code restore} turns a broken one into
 * {@code StoredStateInvalid}, and every command's commit into an {@link AssertionError}, so no
 * command can leave a state that {@code restore} refuses.
 */
final class StopMessagesInvariants {

    private StopMessagesInvariants() {}

    /**
     * The first invariant the state breaks.
     *
     * @param state the state to check
     * @return the broken rule, with no personal data; empty when every rule holds
     */
    static Optional<String> brokenBy(StopMessagesSnapshot state) {
        List<OutgoingMessage> messages = state.messages();
        Set<OutgoingMessageId> ids = new HashSet<>();
        Set<MessageType> outcomes = EnumSet.noneOf(MessageType.class);
        boolean arrivalGiven = false;
        int[] counts = new int[MessageType.values().length];
        for (OutgoingMessage message : messages) {
            if (!ids.add(message.id())) {
                return Optional.of("each message has its own ID");
            }
            MessageType type = message.type();
            counts[type.ordinal()]++;
            if (message.sequence() != counts[type.ordinal()] || message.sequence() > type.mostPerStop()) {
                return Optional.of("each type is numbered from 1, up to its most per stop (NFR-191, FR-384)");
            }
            if (type == MessageType.DELIVERED || type == MessageType.MISSED_YOU) {
                outcomes.add(type);
            }
            if (message.arrivalSent().isPresent() != type.givesAnArrival()) {
                return Optional.of("only out for delivery and time changed give an arrival");
            }
            if (message.asksForRating() && type != MessageType.DELIVERED) {
                return Optional.of("only a delivered message asks for a rating");
            }
            if (type == MessageType.TIME_CHANGED && !arrivalGiven) {
                return Optional.of("a time changed message follows one that gave an arrival (FR-353)");
            }
            arrivalGiven |= type.givesAnArrival();
            if (message.state() instanceof MessageState.Pending pending
                    && !pending.since().equals(message.triggeredAt())) {
                return Optional.of("a pending message is pending since its trigger");
            }
        }
        if (outcomes.size() > 1) {
            return Optional.of("a stop gets one outcome message, for its first outcome (FR-374)");
        }
        return Optional.empty();
    }
}
