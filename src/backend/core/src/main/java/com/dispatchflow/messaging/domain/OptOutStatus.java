package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Where a phone stands, by its opt-out history (FR-363, FR-380, NFR-202): the latest entry wins, so
 * a dispatcher's opt-in after an opt-out lets messages go again (ADR-108). It also decides the
 * entries a reply or a dispatcher adds.
 *
 * <p>The latest entry is the one appended last, whatever its time: a STOP that is processed after a
 * dispatcher's opt-in opts the phone out, even when the reply was received before the opt-in
 * (ADR-108).
 */
public final class OptOutStatus implements PersonalData {

    /** How recently a phone must have been messaged for its opt-out to be confirmed (FR-363). */
    public static final Duration CONFIRMATION_WINDOW = Duration.ofHours(48);

    private final PhoneHash phone;
    private final @Nullable OptOutRecord latest;

    private OptOutStatus(PhoneHash phone, @Nullable OptOutRecord latest) {
        this.phone = phone;
        this.latest = latest;
    }

    /**
     * Reads a phone's history.
     *
     * @param phone the phone's keyed hash
     * @param history every entry for the phone, in the order appended; may be empty
     * @return where the phone stands
     * @throws AssertionError when an entry is for another phone: the use case read the wrong
     *     history, a bug rather than a refusal
     */
    public static OptOutStatus of(PhoneHash phone, List<OptOutRecord> history) {
        Objects.requireNonNull(phone, "phone");
        OptOutRecord latest = null;
        for (OptOutRecord entry : List.copyOf(history)) {
            if (!entry.phone().equals(phone)) {
                throw new AssertionError("an opt-out history is read for one phone");
            }
            latest = entry;
        }
        return new OptOutStatus(phone, latest);
    }

    /**
     * Whether messages may go to the phone.
     *
     * @return {@link PhoneConsent#OPTED_OUT} when the latest entry is an opt-out
     */
    public PhoneConsent consent() {
        return latest != null && latest.change() == OptOutRecord.Change.OPTED_OUT
                ? PhoneConsent.OPTED_OUT
                : PhoneConsent.MAY_BE_MESSAGED;
    }

    /**
     * A reply's opt-out word opts the phone out at once (FR-363). A phone that had not opted out
     * gets one confirmation when it was messaged in the 48 hours up to the reply, and while
     * messages are on, since a change of the switch applies to every message triggered after it
     * (FR-350, ADR-108). A phone already opted out stays so, with no entry and no confirmation.
     *
     * @param at when the reply was received
     * @param lastMessaged when a message to the phone was last accepted by the provider, if ever
     * @param messages whether messages are on
     * @return the entry to append and whether the confirmation is due
     */
    public ReplyOptOut optOutByReply(Instant at, Optional<Instant> lastMessaged, Switch messages) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(lastMessaged, "lastMessaged");
        Objects.requireNonNull(messages, "messages");
        if (consent() == PhoneConsent.OPTED_OUT) {
            return new ReplyOptOut(Optional.empty(), false);
        }
        boolean recentlyMessaged = lastMessaged
                .filter(sent -> !sent.isBefore(at.minus(CONFIRMATION_WINDOW)))
                .isPresent();
        return new ReplyOptOut(Optional.of(OptOutRecord.byReply(phone, at)), recentlyMessaged && messages == Switch.ON);
    }

    /**
     * A dispatcher records an opt-out at the person's request by phone (FR-380).
     *
     * @param dispatcher who records it
     * @param at when
     * @return the entry to append; empty when the phone has already opted out
     */
    public Optional<OptOutRecord> recordByDispatcher(AccountId dispatcher, Instant at) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(at, "at");
        return consent() == PhoneConsent.OPTED_OUT
                ? Optional.empty()
                : Optional.of(OptOutRecord.recordedBy(phone, dispatcher, at));
    }

    /**
     * A dispatcher removes an opt-out at the person's request by phone, by appending an opt-in
     * (FR-380, ADR-108).
     *
     * @param dispatcher who removes it
     * @param at when
     * @return the opt-in to append; empty when no opt-out stands
     */
    public Optional<OptOutRecord> removeByDispatcher(AccountId dispatcher, Instant at) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(at, "at");
        return consent() == PhoneConsent.OPTED_OUT
                ? Optional.of(OptOutRecord.removedBy(phone, dispatcher, at))
                : Optional.empty();
    }

    /**
     * A form that names the phone's standing and hides the phone.
     *
     * @return the status without the phone
     */
    @Override
    public String toString() {
        return "OptOutStatus[" + consent() + "]";
    }
}
