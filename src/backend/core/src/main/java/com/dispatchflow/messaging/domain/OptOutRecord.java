package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * One entry in a phone's opt-out history, a record appended through {@code OptOutList} and never
 * changed (DL §3.2). A phone opts out by an opt-out word (FR-363), or a dispatcher records the
 * opt-out at the person's request by phone; only a dispatcher removes one, by appending an opt-in
 * (FR-380, ADR-108). The latest entry for a phone decides ({@link OptOutStatus}).
 *
 * <p>It is a class with factories, not a record, so an opt-in by a reply can't be built.
 */
public final class OptOutRecord implements PersonalData {

    /** What an entry does to the phone. */
    public enum Change {
        /** The phone gets no further messages (FR-363). */
        OPTED_OUT,
        /** A dispatcher removed the opt-out (FR-380). */
        OPTED_IN
    }

    private final PhoneHash phone;
    private final Change change;
    private final Instant at;
    private final @Nullable AccountId dispatcher;

    private OptOutRecord(PhoneHash phone, Change change, Instant at, @Nullable AccountId dispatcher) {
        this.phone = Objects.requireNonNull(phone, "phone");
        this.change = Objects.requireNonNull(change, "change");
        this.at = Objects.requireNonNull(at, "at");
        this.dispatcher = dispatcher;
    }

    /**
     * The phone opted out by replying with an opt-out word (FR-363).
     *
     * @param phone the phone's keyed hash
     * @param at when the reply was received
     * @return the entry
     */
    static OptOutRecord byReply(PhoneHash phone, Instant at) {
        return new OptOutRecord(phone, Change.OPTED_OUT, at, null);
    }

    /**
     * A dispatcher recorded an opt-out at the person's request by phone (FR-380).
     *
     * @param phone the phone's keyed hash
     * @param dispatcher who recorded it
     * @param at when
     * @return the entry
     */
    static OptOutRecord recordedBy(PhoneHash phone, AccountId dispatcher, Instant at) {
        return new OptOutRecord(phone, Change.OPTED_OUT, at, Objects.requireNonNull(dispatcher, "dispatcher"));
    }

    /**
     * A dispatcher removed an opt-out at the person's request by phone (FR-380, ADR-108).
     *
     * @param phone the phone's keyed hash
     * @param dispatcher who removed it
     * @param at when
     * @return the entry, an opt-in
     */
    static OptOutRecord removedBy(PhoneHash phone, AccountId dispatcher, Instant at) {
        return new OptOutRecord(phone, Change.OPTED_IN, at, Objects.requireNonNull(dispatcher, "dispatcher"));
    }

    /**
     * Rebuilds a stored entry.
     *
     * @param phone the phone's keyed hash
     * @param change what the entry does
     * @param at when it was made
     * @param dispatcher the dispatcher who made it; empty for an opt-out by reply
     * @return the entry
     * @throws StoredStateInvalid for an opt-in with no dispatcher, which only a dispatcher makes
     */
    public static OptOutRecord ofStored(PhoneHash phone, Change change, Instant at, Optional<AccountId> dispatcher) {
        if (change == Change.OPTED_IN && dispatcher.isEmpty()) {
            throw new StoredStateInvalid(OptOutRecord.class, "only a dispatcher removes an opt-out");
        }
        return new OptOutRecord(phone, change, at, dispatcher.orElse(null));
    }

    /**
     * The phone the entry is about.
     *
     * @return its keyed hash
     */
    public PhoneHash phone() {
        return phone;
    }

    /**
     * What the entry does.
     *
     * @return opted out or opted in
     */
    public Change change() {
        return change;
    }

    /**
     * When the entry was made.
     *
     * @return the time
     */
    public Instant at() {
        return at;
    }

    /**
     * Who made the entry (FR-380).
     *
     * @return the dispatcher; empty for an opt-out by the recipient's reply
     */
    public Optional<AccountId> dispatcher() {
        return Optional.ofNullable(dispatcher);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof OptOutRecord that
                && phone.equals(that.phone)
                && change == that.change
                && at.equals(that.at)
                && Objects.equals(dispatcher, that.dispatcher);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phone, change, at, dispatcher);
    }

    /**
     * A form that names the change and hides the phone.
     *
     * @return the entry without its phone
     */
    @Override
    public String toString() {
        return "OptOutRecord[phone=redacted, change=" + change + ", at=" + at + ", dispatcher=" + dispatcher + "]";
    }
}
