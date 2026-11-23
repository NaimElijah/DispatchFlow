package com.dispatchflow.messaging.domain.message;

import static com.dispatchflow.messaging.domain.MessagingFixtures.STOP;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TO_MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.at;
import static com.dispatchflow.messaging.domain.MessagingFixtures.conditions;
import static com.dispatchflow.messaging.domain.MessagingFixtures.freshIds;
import static com.dispatchflow.messaging.domain.MessagingFixtures.messageId;
import static com.dispatchflow.messaging.domain.MessagingFixtures.on;
import static com.dispatchflow.messaging.domain.MessagingFixtures.projected;
import static com.dispatchflow.shared.AsSaved.asSaved;

import com.dispatchflow.messaging.domain.MessagingFixtures;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.message.ArrivalFacts.PreviousStop;
import com.dispatchflow.messaging.domain.message.StopMessages.Claim;
import com.dispatchflow.messaging.domain.message.StopMessages.ProviderRepeats;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The test builder of {@link StopMessages}: {@code aStopMessages()} is a saved stop with no message.
 * Each {@code with…} step triggers one message at 10:00, to {@link MessagingFixtures#MOBILE} unless
 * it names a phone, then runs the sender's commands that move it to the state given, so every state
 * is one the commands reach (ADR-127 decision 2). {@link #build} replays the steps on a new
 * aggregate each time and saves it, so one builder may build many.
 *
 * <p>A state's time is when the commands reach it: the claim, and then the provider's answer, both
 * at that time. So a message is pending only since its trigger, sending, accepted or of unknown
 * outcome only up to 15 minutes after it, and dropped as stale only later (NFR-190).
 */
final class StopMessagesBuilder {

    private static final DailySendCap ROOM = new DailySendCap(0);

    private final List<Consumer<StopMessages>> steps = new ArrayList<>();

    private StopMessagesBuilder() {}

    static StopMessagesBuilder aStopMessages() {
        return new StopMessagesBuilder();
    }

    static MessageState pendingSinceTen() {
        return new MessageState.Pending(TEN);
    }

    /** "Out for delivery" for 11:00, its route started at 10:00. */
    StopMessagesBuilder withOutForDelivery(int n, MessageState state) {
        return adding(
                n,
                state,
                messages -> messages.onRouteStarted(
                        messageId(n), TO_MOBILE, on(), StopProgress.NOT_REACHED, projected(at(60)), TEN));
    }

    /** "Time changed" for 11:30, projected at 10:00; it follows this builder's "out for delivery". */
    StopMessagesBuilder withTimeChanged(int n, MessageState state) {
        return adding(
                n,
                state,
                messages -> messages.onArrivalProjected(freshIds(n), TO_MOBILE, on(), inProgress(at(90)), TEN));
    }

    /**
     * A message of a type that gives no arrival, triggered at 10:00. "Arriving soon" is projected
     * for 10:10, so it may come only before an arrival was sent: after one, the projection adds a
     * "time changed" too.
     */
    StopMessagesBuilder with(int n, MessageType type, MessageState state) {
        Consumer<StopMessages> trigger = switch (type) {
            case ARRIVING_SOON ->
                messages -> messages.onArrivalProjected(freshIds(n), TO_MOBILE, on(), inProgress(at(10)), TEN);
            case DELIVERY_CHANGED ->
                messages -> messages.onDeliveryChanged(
                        messageId(n), TO_MOBILE, on(), RouteProgress.IN_PROGRESS, StopProgress.NOT_REACHED, TEN);
            case MISSED_YOU ->
                messages -> messages.onOutcome(messageId(n), TO_MOBILE, on(), new ReportedOutcome.Failed(), TEN);
            case DELIVERED ->
                messages ->
                        messages.onOutcome(messageId(n), TO_MOBILE, on(), new ReportedOutcome.Delivered(false), TEN);
            case OUT_FOR_DELIVERY, TIME_CHANGED -> throw new AssertionError("gives an arrival");
        };
        return adding(n, state, trigger);
    }

    /** "Delivered" with the rating question on, so it asks for a rating, to the phone given. */
    StopMessagesBuilder withDeliveredAskingForARating(int n, RecipientPhone to, MessageState state) {
        return adding(
                n,
                state,
                messages -> messages.onOutcome(
                        messageId(n),
                        to,
                        conditions(Switch.ON, Switch.ON, PhoneConsent.MAY_BE_MESSAGED),
                        new ReportedOutcome.Delivered(false),
                        TEN));
    }

    /** A new aggregate that ran every step, saved as a use case would have saved it. */
    StopMessages build() {
        StopMessages messages = StopMessages.open(STOP);
        steps.forEach(step -> step.accept(messages));
        return asSaved(messages);
    }

    private StopMessagesBuilder adding(int n, MessageState state, Consumer<StopMessages> trigger) {
        steps.add(messages -> {
            int before = messages.messages().size();
            trigger.accept(messages);
            if (messages.messages().size() != before + 1) {
                throw new AssertionError("a builder step adds exactly one message");
            }
            reach(messages, messageId(n), state);
            if (messages.messages().stream()
                    .noneMatch(message ->
                            message.id().equals(messageId(n)) && message.state().equals(state))) {
                throw new AssertionError("the commands did not reach the state asked for");
            }
        });
        return this;
    }

    /** Runs the sender's commands that take a pending message to {@code state}, at its time. */
    private static void reach(StopMessages messages, OutgoingMessageId id, MessageState state) {
        switch (state) {
            case MessageState.Pending pending -> {}
            case MessageState.Sending sending -> claim(messages, id, sending);
            case MessageState.Accepted accepted -> {
                claim(messages, id, accepted);
                messages.accepted(id, accepted.reference(), accepted.since());
            }
            case MessageState.Unknown unknown -> {
                claim(messages, id, unknown);
                messages.outcomeUnknown(id, ProviderRepeats.DROPPED_BY_REFERENCE, unknown.since());
            }
            case MessageState.Dropped dropped -> drop(messages, id, dropped);
        }
    }

    private static void drop(StopMessages messages, OutgoingMessageId id, MessageState.Dropped dropped) {
        switch (dropped.cause()) {
            case REFUSED -> {
                claim(messages, id, dropped);
                messages.refused(id, dropped.since());
            }
            case OUTCOME_UNKNOWN -> {
                claim(messages, id, dropped);
                messages.outcomeUnknown(id, ProviderRepeats.NOT_DROPPED, dropped.since());
            }
            case OPTED_OUT -> messages.claim(id, ROOM, PhoneConsent.OPTED_OUT, dropped.since());
            case STALE -> messages.claim(id, ROOM, PhoneConsent.MAY_BE_MESSAGED, dropped.since());
            // The sender's sweep drops every unended message past its 15 minutes, not this one alone.
            case STALE_WHILE_SENDING -> {
                messages.claim(id, ROOM, PhoneConsent.MAY_BE_MESSAGED, TEN);
                messages.dropStale(dropped.since());
            }
        }
    }

    private static void claim(StopMessages messages, OutgoingMessageId id, MessageState state) {
        if (messages.claim(id, ROOM, PhoneConsent.MAY_BE_MESSAGED, state.since()) != Claim.CLAIMED) {
            throw new AssertionError("a message is claimed only up to 15 minutes after its trigger");
        }
    }

    private static ArrivalFacts inProgress(Instant arrival) {
        return new ArrivalFacts(
                RouteProgress.IN_PROGRESS, PreviousStop.HAS_OUTCOME, StopProgress.NOT_REACHED, projected(arrival));
    }
}
