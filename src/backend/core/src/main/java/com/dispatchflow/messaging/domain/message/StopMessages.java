package com.dispatchflow.messaging.domain.message;

import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.MessagingError.MessageNotClaimable;
import com.dispatchflow.messaging.domain.MessagingError.MessageNotSending;
import com.dispatchflow.messaging.domain.MessagingError.UnknownMessage;
import com.dispatchflow.messaging.domain.MessagingRetention;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.ProjectedArrival;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.messaging.domain.RatingQuestion;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.messaging.domain.Wording;
import com.dispatchflow.messaging.domain.message.MessageState.DropCause;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every message to one stop's recipient, and the rules on which go and how often (DL §4.6, §16),
 * keyed by the stop's ID. Its triggers add a message only when the rules allow one, and otherwise
 * do nothing, because a message that may not go is not an error. The sender then moves each
 * message through its lifecycle (DL §9.5).
 *
 * <p>A message is added only while messages were on when it was triggered (FR-350), to an Israeli
 * mobile (FR-378) that has not opted out (FR-363), and at most once per type, except "time
 * changed", at most twice (NFR-191, FR-384). The messages of FR-351 to FR-354 and FR-372 stop once
 * the stop is arrived at or has an outcome (FR-373). Only the first outcome is messaged, and a
 * correction sends nothing: Messaging is never told of one (FR-374).
 *
 * <p>Each message keeps the phone it was triggered for, and is sent to that phone: a later trigger
 * that names another phone adds its message there and leaves the earlier ones alone (ADR-117).
 *
 * <p>It raises no event. Every method checks all its rules before it changes anything.
 */
public final class StopMessages extends AggregateRoot implements PersonalData {

    /** How long after its trigger a message may still be accepted (NFR-190). */
    public static final Duration STALE_AFTER = Duration.ofMinutes(15);

    /** How near a projected arrival makes the stop "arriving soon" (FR-352). */
    public static final Duration ARRIVING_SOON_WITHIN = Duration.ofMinutes(15);

    /** How far a projection must move from the arrival last sent for "time changed" (FR-353). */
    public static final Duration TIME_CHANGE_THRESHOLD = Duration.ofMinutes(30);

    /** What a claim did (DL §9.5). */
    public enum Claim {
        /** The message is sending: call the provider now. */
        CLAIMED,
        /** The message was dropped instead: it is stale (NFR-190) or its phone opted out (FR-363). */
        DROPPED,
        /** The day's cap is reached, so the message waits, unchanged (NFR-196). */
        WAITING
    }

    /** Whether the SMS provider drops a repeated send by DispatchFlow's reference (NFR-198). */
    public enum ProviderRepeats {
        /** It does, so a send whose outcome is unknown may be sent again. */
        DROPPED_BY_REFERENCE,
        /** It doesn't, so such a send counts as dropped. */
        NOT_DROPPED
    }

    private final StopId stop;
    private List<OutgoingMessage> messages;

    private StopMessages(StopMessagesSnapshot state) {
        super(state.version());
        this.stop = state.stop();
        this.messages = state.messages();
    }

    /**
     * Starts the messages of a stop that has none yet, when its first trigger arrives (DL §6.2).
     *
     * @param stop the stop
     * @return the aggregate, with no message, never saved
     */
    public static StopMessages open(StopId stop) {
        return new StopMessages(new StopMessagesSnapshot(Objects.requireNonNull(stop, "stop"), null, List.of()));
    }

    /**
     * Rebuilds a stop's messages from storage, checking the invariants that hold at rest: each
     * message has its own ID; each type is numbered from 1 up to its most per stop; only "out for
     * delivery" and "time changed" give an arrival, and a "time changed" follows one that did;
     * only "delivered" asks for a rating; one outcome message at most; and a pending message is
     * pending since its trigger. Only persistence adapters call it (DL §3.5).
     *
     * @param stored the stored state
     * @return the aggregate at the stored version
     * @throws StoredStateInvalid when the stored state breaks one of those invariants
     */
    public static StopMessages restore(StopMessagesSnapshot stored) {
        StopMessagesInvariants.brokenBy(stored).ifPresent(broken -> {
            throw new StoredStateInvalid(StopMessages.class, broken);
        });
        return new StopMessages(stored);
    }

    // --- triggers

    /**
     * The stop's route started: "out for delivery", with the projected arrival rounded to the
     * nearest quarter hour, ±30 minutes, unless the stop is arrived at or has an outcome (FR-351,
     * FR-373).
     *
     * @param id the new message's ID, used only if a message is added
     * @param to the stop's phone, chosen by FR-348
     * @param conditions the settings, company and phone standing now
     * @param progress how far the stop has come
     * @param arrival the stop's projected arrival, carried forward or, for a stop the route's first
     *     report skipped, planned (ADR-114)
     * @param now when the trigger is recorded
     */
    public void onRouteStarted(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            StopProgress progress,
            ProjectedArrival arrival,
            Instant now) {
        outForDelivery(id, to, conditions, progress, arrival, now);
    }

    /**
     * The stop joined a route in progress, such as a new order or a put-back: "out for delivery",
     * as {@link #onRouteStarted} gives it (FR-372, FR-373). A stop that already had it on another
     * route gets no second one (NFR-191).
     *
     * @param id the new message's ID, used only if a message is added
     * @param to the stop's phone, chosen by FR-348
     * @param conditions the settings, company and phone standing now
     * @param progress how far the stop has come
     * @param arrival the stop's projected arrival on its new route
     * @param now when the trigger is recorded
     */
    public void onJoinedStartedRoute(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            StopProgress progress,
            ProjectedArrival arrival,
            Instant now) {
        outForDelivery(id, to, conditions, progress, arrival, now);
    }

    /**
     * Messaging's minute job projected the stop's arrival (DL §10.3). It may add two messages, each
     * judged on its own:
     *
     * <ul>
     *   <li>"arriving soon", when the projection is 15 minutes away or less, the route is in
     *       progress, and the stop before it in plan order, if any, has an outcome (FR-352);
     *   <li>"time changed", with a new window, once the route has started, when the projection is
     *       30 minutes or more from the arrival last sent, at most twice (FR-353, FR-384). The
     *       arrival last sent is that of the latest "out for delivery" or "time changed" not
     *       dropped (ADR-108).
     * </ul>
     *
     * Neither goes once the stop is arrived at or has an outcome (FR-373), and neither goes on a
     * skipped stop's planned arrival: held at now, it moves with the clock alone, so it would say
     * "arriving soon" at once and "time changed" every half hour (ADR-114). "Out for delivery" may
     * use one.
     *
     * @param ids new message IDs, one drawn per message added, "arriving soon" first
     * @param to the stop's phone, chosen by FR-348
     * @param conditions the settings, company and phone standing now
     * @param facts the stop, its route, the stop before it and the projection
     * @param now when the projection was made
     */
    public void onArrivalProjected(
            FreshIds ids, RecipientPhone to, SendingConditions conditions, ArrivalFacts facts, Instant now) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(now, "now");
        Optional<PhoneNumber> phone = phoneToMessage(to, conditions);
        if (phone.isEmpty()
                || !facts.stop().awaitsDelivery()
                || !facts.arrival().isCarriedForward()) {
            return;
        }
        List<OutgoingMessage> next = new ArrayList<>(messages);
        if (arrivingSoon(facts, now) && countOf(MessageType.ARRIVING_SOON) == 0) {
            next.add(pending(
                    OutgoingMessageId.of(ids.next()),
                    phone.get(),
                    MessageType.ARRIVING_SOON,
                    conditions.write(new Wording.ArrivingSoon()),
                    Optional.empty(),
                    false,
                    now));
        }
        Optional<QuarterHour> lastSent = lastArrivalSent();
        boolean moved = lastSent.filter(sent -> sent.distanceTo(facts.arrival()).compareTo(TIME_CHANGE_THRESHOLD) >= 0)
                .isPresent();
        if (facts.route().hasStarted()
                && moved
                && countOf(MessageType.TIME_CHANGED) < MessageType.TIME_CHANGED.mostPerStop()) {
            QuarterHour arrival = QuarterHour.nearest(facts.arrival());
            next.add(pending(
                    OutgoingMessageId.of(ids.next()),
                    phone.get(),
                    MessageType.TIME_CHANGED,
                    conditions.write(new Wording.TimeChanged(arrival)),
                    Optional.of(arrival),
                    false,
                    now));
        }
        if (next.size() > messages.size()) {
            commit(next);
        }
    }

    /**
     * The stop's order was dropped, moved to another date or cancelled: "your delivery changed",
     * with the dispatch phone, once the route has started, unless the stop is arrived at or has an
     * outcome (FR-354, FR-373).
     *
     * @param id the new message's ID, used only if a message is added
     * @param to the stop's phone, chosen by FR-348
     * @param conditions the settings, company and phone standing now
     * @param route how far the route that held the stop has come
     * @param progress how far the stop has come
     * @param now when the trigger is recorded
     */
    public void onDeliveryChanged(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            RouteProgress route,
            StopProgress progress,
            Instant now) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(progress, "progress");
        requireTrigger(id, to, conditions, now);
        if (!route.hasStarted() || !progress.awaitsDelivery()) {
            return;
        }
        addOnce(
                id,
                to,
                conditions,
                MessageType.DELIVERY_CHANGED,
                new Wording.DeliveryChanged(conditions.dispatchPhone()),
                Optional.empty(),
                false,
                now);
    }

    /**
     * The stop's first outcome was recorded, by its driver or by a dispatcher: "delivered", saying
     * whether it was left at the door and, while the rating question is on, asking for a rating; or
     * "missed you", with the dispatch phone and not the reason (FR-355, FR-356, FR-374). A stop
     * gets one of the two at most; a repeated delivery of the outcome adds nothing.
     *
     * @param id the new message's ID, used only if a message is added
     * @param to the stop's phone, chosen by FR-348
     * @param conditions the settings, company and phone standing now
     * @param outcome the first outcome
     * @param now when the trigger is recorded
     */
    public void onOutcome(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            ReportedOutcome outcome,
            Instant now) {
        Objects.requireNonNull(outcome, "outcome");
        requireTrigger(id, to, conditions, now);
        if (countOf(MessageType.DELIVERED) + countOf(MessageType.MISSED_YOU) > 0) {
            return;
        }
        switch (outcome) {
            case ReportedOutcome.Delivered(boolean leftAtDoor) -> {
                boolean asks = conditions.ratingQuestion() == Switch.ON;
                addOnce(
                        id,
                        to,
                        conditions,
                        MessageType.DELIVERED,
                        new Wording.Delivered(leftAtDoor, asks),
                        Optional.empty(),
                        asks,
                        now);
            }
            case ReportedOutcome.Failed failed ->
                addOnce(
                        id,
                        to,
                        conditions,
                        MessageType.MISSED_YOU,
                        new Wording.MissedYou(conditions.dispatchPhone()),
                        Optional.empty(),
                        false,
                        now);
        }
    }

    // --- the sender

    /**
     * The sender claims a message to send it (DL §9.5). A message more than 15 minutes past its
     * trigger is dropped instead (NFR-190), and so is one whose phone opted out since its trigger
     * (FR-363). A message of unknown outcome dropped either way is dropped with its outcome unknown,
     * because it was claimed before and may have left, so it still counts toward the daily cap
     * (NFR-196, ADR-108). While the day's cap is reached the message waits, unchanged (NFR-196).
     *
     * @param id the message
     * @param cap the day's claimed messages that count toward the cap
     * @param consent the standing now of the message's own phone, the one it was triggered for
     *     (ADR-117)
     * @param now when the sender claims it
     * @return what the claim did
     * @throws RuleViolation with {@link UnknownMessage} for a message the stop doesn't have, or
     *     {@link MessageNotClaimable} for one neither pending nor retryable after an unknown outcome
     */
    public Claim claim(OutgoingMessageId id, DailySendCap cap, PhoneConsent consent, Instant now) {
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(consent, "consent");
        Objects.requireNonNull(now, "now");
        OutgoingMessage message = find(id);
        if (!(message.state() instanceof MessageState.Pending || message.state() instanceof MessageState.Unknown)) {
            throw new RuleViolation(new MessageNotClaimable(id));
        }
        if (isStale(message, now)) {
            replace(message.in(staleDrop(message.state(), now)));
            return Claim.DROPPED;
        }
        if (consent == PhoneConsent.OPTED_OUT) {
            replace(message.in(optedOutDrop(message.state(), now)));
            return Claim.DROPPED;
        }
        if (!cap.hasRoom()) {
            return Claim.WAITING;
        }
        replace(message.in(new MessageState.Sending(now)));
        return Claim.CLAIMED;
    }

    /**
     * The provider accepted a message the sender is sending, or one dropped as stale while it was
     * sending: that one really left, and the fact stands (ADR-065, ADR-108). A "delivered" message
     * that asks for a rating then opens a rating request, which the use case creates in the same
     * transaction (DL §6.2), for the phone that message went to (ADR-117).
     *
     * @param id the message
     * @param reference the provider's identifier for it
     * @param now when the provider accepted it
     * @return the rating question to open a request for; empty for any other message
     * @throws RuleViolation with {@link UnknownMessage}, or {@link MessageNotSending} for a message
     *     neither sending nor dropped as stale while sending
     */
    public Optional<RatingQuestion> accepted(OutgoingMessageId id, ProviderReference reference, Instant now) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(now, "now");
        OutgoingMessage message = find(id);
        boolean leftLate = message.state() instanceof MessageState.Dropped(Instant at, DropCause cause)
                && cause == DropCause.STALE_WHILE_SENDING;
        if (!(message.state() instanceof MessageState.Sending) && !leftLate) {
            throw new RuleViolation(new MessageNotSending(id));
        }
        replace(message.in(new MessageState.Accepted(now, reference)));
        if (message.asksForRating()) {
            return Optional.of(new RatingQuestion(stop, message.phone(), now));
        }
        return Optional.empty();
    }

    /**
     * The provider refused a message: it is dropped (DL §9.5).
     *
     * @param id the message
     * @param now when the provider answered
     * @throws RuleViolation with {@link UnknownMessage} or {@link MessageNotSending}
     */
    public void refused(OutgoingMessageId id, Instant now) {
        Objects.requireNonNull(now, "now");
        OutgoingMessage message = requireSending(id);
        replace(message.in(new MessageState.Dropped(now, DropCause.REFUSED)));
    }

    /**
     * A send timed out, or the sender stopped mid-call. It may be sent again only if the provider
     * drops repeats by DispatchFlow's reference; otherwise it counts as dropped (NFR-198).
     *
     * @param id the message
     * @param repeats whether the provider drops repeats
     * @param now when the outcome became unknown
     * @throws RuleViolation with {@link UnknownMessage} or {@link MessageNotSending}
     */
    public void outcomeUnknown(OutgoingMessageId id, ProviderRepeats repeats, Instant now) {
        Objects.requireNonNull(repeats, "repeats");
        Objects.requireNonNull(now, "now");
        OutgoingMessage message = requireSending(id);
        replace(message.in(
                repeats == ProviderRepeats.DROPPED_BY_REFERENCE
                        ? new MessageState.Unknown(now)
                        : new MessageState.Dropped(now, DropCause.OUTCOME_UNKNOWN)));
    }

    /**
     * Drops every message not accepted more than 15 minutes after its trigger: pending, sending or
     * of unknown outcome (NFR-190). A message exactly 15 minutes past its trigger may still be
     * accepted.
     *
     * @param now when the sender runs
     * @return how many messages it dropped, which NFR-190 measures
     */
    public int dropStale(Instant now) {
        Objects.requireNonNull(now, "now");
        List<OutgoingMessage> next = new ArrayList<>(messages.size());
        int dropped = 0;
        for (OutgoingMessage message : messages) {
            if (!message.state().hasEnded() && isStale(message, now)) {
                next.add(message.in(staleDrop(message.state(), now)));
                dropped++;
            } else {
                next.add(message);
            }
        }
        if (dropped > 0) {
            commit(next);
        }
        return dropped;
    }

    // --- queries

    /**
     * The stop the messages are about.
     *
     * @return its ID, which keys the aggregate
     */
    public StopId stop() {
        return stop;
    }

    /**
     * When the retention of the stop's messages ends: 90 days after the last of them reached its
     * state (NFR-184, ADR-108). The retention job deletes the stop's messages then.
     *
     * @return the end; empty for a stop with no message
     */
    public Optional<Instant> retentionEndsAt() {
        return messages.stream()
                .map(message -> message.state().since())
                .max(Instant::compareTo)
                .map(MessagingRetention::endOf);
    }

    /**
     * The stop's messages as stored.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public StopMessagesSnapshot snapshot() {
        return new StopMessagesSnapshot(stop, savedVersion(), messages);
    }

    /**
     * A form that names the stop and leaves the phones and the texts out.
     *
     * @return the aggregate without personal data
     */
    @Override
    public String toString() {
        return "StopMessages[" + snapshot() + "]";
    }

    // --- helpers

    private void outForDelivery(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            StopProgress progress,
            ProjectedArrival projected,
            Instant now) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(projected, "projected");
        requireTrigger(id, to, conditions, now);
        if (!progress.awaitsDelivery()) {
            return;
        }
        QuarterHour arrival = QuarterHour.nearest(projected);
        addOnce(
                id,
                to,
                conditions,
                MessageType.OUT_FOR_DELIVERY,
                new Wording.OutForDelivery(arrival),
                Optional.of(arrival),
                false,
                now);
    }

    private static void requireTrigger(
            OutgoingMessageId id, RecipientPhone to, SendingConditions conditions, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(now, "now");
    }

    private void addOnce(
            OutgoingMessageId id,
            RecipientPhone to,
            SendingConditions conditions,
            MessageType type,
            Wording wording,
            Optional<QuarterHour> arrival,
            boolean asksForRating,
            Instant now) {
        Optional<PhoneNumber> phone = phoneToMessage(to, conditions);
        if (phone.isEmpty() || countOf(type) >= type.mostPerStop()) {
            return;
        }
        List<OutgoingMessage> next = new ArrayList<>(messages);
        next.add(pending(id, phone.get(), type, conditions.write(wording), arrival, asksForRating, now));
        commit(next);
    }

    /** The phone a new message goes to, while messages may go to it (FR-350, FR-363, FR-378). */
    private static Optional<PhoneNumber> phoneToMessage(RecipientPhone to, SendingConditions conditions) {
        if (conditions.mayMessage() && to.takesMessages() && to instanceof RecipientPhone.Known(PhoneNumber phone)) {
            return Optional.of(phone);
        }
        return Optional.empty();
    }

    private OutgoingMessage pending(
            OutgoingMessageId id,
            PhoneNumber phone,
            MessageType type,
            MessageText text,
            Optional<QuarterHour> arrival,
            boolean asksForRating,
            Instant now) {
        if (messages.stream().anyMatch(message -> message.id().equals(id))) {
            throw new AssertionError("a new message needs a new ID");
        }
        return new OutgoingMessage(
                id, type, countOf(type) + 1, phone, text, arrival, asksForRating, now, new MessageState.Pending(now));
    }

    private static boolean arrivingSoon(ArrivalFacts facts, Instant now) {
        return facts.route() == RouteProgress.IN_PROGRESS
                && facts.previousStop() != ArrivalFacts.PreviousStop.NO_OUTCOME
                && Duration.between(now, facts.arrival().at()).compareTo(ARRIVING_SOON_WITHIN) <= 0;
    }

    private Optional<QuarterHour> lastArrivalSent() {
        Optional<QuarterHour> last = Optional.empty();
        for (OutgoingMessage message : messages) {
            if (message.arrivalSent().isPresent() && !(message.state() instanceof MessageState.Dropped)) {
                last = message.arrivalSent();
            }
        }
        return last;
    }

    private int countOf(MessageType type) {
        return (int) messages.stream().filter(message -> message.type() == type).count();
    }

    /** A pending message never left; a sending one may have; an unknown one's outcome stays unknown. */
    private static MessageState staleDrop(MessageState state, Instant now) {
        DropCause cause = switch (state) {
            case MessageState.Sending sending -> DropCause.STALE_WHILE_SENDING;
            case MessageState.Unknown unknown -> DropCause.OUTCOME_UNKNOWN;
            default -> DropCause.STALE;
        };
        return new MessageState.Dropped(now, cause);
    }

    /**
     * An unknown message was claimed once, so it may have left and keeps counting toward the daily
     * cap (NFR-196, ADR-108); only a pending one is dropped as opted out.
     */
    private static MessageState optedOutDrop(MessageState state, Instant now) {
        DropCause cause = state instanceof MessageState.Unknown ? DropCause.OUTCOME_UNKNOWN : DropCause.OPTED_OUT;
        return new MessageState.Dropped(now, cause);
    }

    private static boolean isStale(OutgoingMessage message, Instant now) {
        return now.isAfter(message.triggeredAt().plus(STALE_AFTER));
    }

    private OutgoingMessage find(OutgoingMessageId id) {
        Objects.requireNonNull(id, "id");
        return messages.stream()
                .filter(message -> message.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuleViolation(new UnknownMessage(id)));
    }

    private OutgoingMessage requireSending(OutgoingMessageId id) {
        OutgoingMessage message = find(id);
        if (!(message.state() instanceof MessageState.Sending)) {
            throw new RuleViolation(new MessageNotSending(id));
        }
        return message;
    }

    private void replace(OutgoingMessage changed) {
        List<OutgoingMessage> next = new ArrayList<>(messages.size());
        for (OutgoingMessage message : messages) {
            next.add(message.id().equals(changed.id()) ? changed : message);
        }
        commit(next);
    }

    /**
     * Replaces the state with the one a command built, after checking it holds every at-rest
     * invariant. Every rule a caller can break was checked before this point, so a broken invariant
     * here is a bug in the command, not a refusal.
     */
    private void commit(List<OutgoingMessage> nextMessages) {
        StopMessagesSnapshot candidate = new StopMessagesSnapshot(stop, savedVersion(), nextMessages);
        StopMessagesInvariants.brokenBy(candidate).ifPresent(broken -> {
            throw new AssertionError("a stop messages command would leave a state that breaks: " + broken);
        });
        messages = candidate.messages();
    }
}
