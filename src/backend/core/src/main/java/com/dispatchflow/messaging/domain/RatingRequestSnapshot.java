package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link RatingRequest}'s whole state: what {@code snapshot()} gives and {@code restore} takes. The
 * repository rebuilds the phone with {@code PhoneNumber.ofStored} and each comment with {@link
 * Comment#ofStored}. It holds the phone and the comments until they are removed, so {@link
 * #toString()} leaves them out.
 *
 * @param id the request's ID
 * @param version the version it was loaded at
 * @param stop the delivered stop the request is about
 * @param phone the phone the question went to; empty once removed (NFR-184)
 * @param acceptedAt when the provider accepted the question, from which the 48 hours run (FR-357)
 * @param rating the delivery's rating, once given
 * @param comments the replies kept as comments, at most 20, in the order received (FR-360)
 * @param reminder what became of the one format reminder (FR-359)
 * @param thankYou what became of the one thank-you (FR-358)
 * @param removedAt when the phone was removed, if it was; each comment goes on its own date
 */
public record RatingRequestSnapshot(
        RatingRequestId id,
        Version version,
        StopId stop,
        Optional<PhoneNumber> phone,
        Instant acceptedAt,
        Optional<RatingRequest.Rated> rating,
        List<ReceivedComment> comments,
        RatingRequest.OneTimeReply reminder,
        RatingRequest.OneTimeReply thankYou,
        Optional<Instant> removedAt)
        implements PersonalData {

    /** Checks every part is present and copies the list. */
    public RatingRequestSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        Objects.requireNonNull(rating, "rating");
        comments = List.copyOf(comments);
        Objects.requireNonNull(reminder, "reminder");
        Objects.requireNonNull(thankYou, "thankYou");
        Objects.requireNonNull(removedAt, "removedAt");
    }

    /**
     * A form that leaves the phone and the comments out.
     *
     * @return the snapshot without personal data
     */
    @Override
    public String toString() {
        return "RatingRequestSnapshot[id=" + id + ", version=" + version + ", stop=" + stop + ", phone="
                + (phone.isPresent() ? "redacted" : "none") + ", acceptedAt=" + acceptedAt + ", rating=" + rating
                + ", comments=" + comments.size() + ", reminder=" + reminder + ", thankYou=" + thankYou
                + ", removedAt=" + removedAt + "]";
    }
}
