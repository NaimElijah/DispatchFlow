package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.MessagingError.BlankComment;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import java.util.Objects;
import java.util.Optional;

/**
 * A recipient's SMS reply, read by fixed rules (FR-363, FR-376, FR-360; ADR-108). It is one of an
 * opt-out word, a rating with or without a comment, a comment that is not a rating, or a reply
 * with nothing visible. What the reply then counts for is decided by the opt-out history ({@link
 * OptOutStatus}) and by the rating request it answers ({@link RatingRequest#answer}).
 */
public sealed interface Reply {

    /**
     * Reads a reply.
     *
     * <ul>
     *   <li>It is an opt-out when its first token, split at whitespace, is "הסר", "הסרה", "להסרה"
     *       or "STOP" in any letter case, once it is in NFKC and its emoji, symbols, format
     *       characters and punctuation are removed (FR-363, ADR-108).
     *   <li>Otherwise it is a rating when, once spaces are trimmed, it starts with a whole number
     *       from 1 to 10 in Western digits, with no leading zero, followed by neither a digit nor a
     *       decimal point and a digit (FR-376). A rating followed by anything is also a comment,
     *       and the comment is the whole trimmed reply: "8 thanks" rates 8 and keeps "8 thanks"
     *       (FR-360).
     *   <li>Otherwise it is a comment, or blank when nothing visible is left.
     * </ul>
     *
     * A comment is kept as {@link Comment#cutFrom} keeps it, so no reply is refused.
     *
     * @param raw the reply's text as the provider delivered it
     * @return the reply
     */
    static Reply parse(String raw) {
        String text = ReplyText.trimmed(Objects.requireNonNull(raw, "raw"));
        if (text.isEmpty()) {
            return new BlankReply();
        }
        if (ReplyText.isOptOutWord(ReplyText.firstToken(text))) {
            return new OptOutWord();
        }
        Optional<Rating> rating = ReplyText.leadingRating(text);
        if (rating.isPresent()) {
            boolean bare = text.equals(Integer.toString(rating.get().value()));
            return new RatingReply(rating.get(), bare ? Optional.empty() : Optional.of(Comment.cutFrom(text)));
        }
        try {
            return new TextReply(Comment.cutFrom(text));
        } catch (RuleViolation refused) {
            if (refused.error() instanceof BlankComment) {
                // Visible to the trim above, invisible to the kernel: a lone variation selector.
                return new BlankReply();
            }
            throw refused;
        }
    }

    /** An opt-out word (FR-363). It is neither a rating nor a comment. */
    record OptOutWord() implements Reply {}

    /**
     * A rating, and the whole reply as a comment unless the reply is the bare number (FR-357,
     * FR-360).
     *
     * @param rating the rating
     * @param comment the reply kept as a comment; empty for a bare rating
     */
    record RatingReply(Rating rating, Optional<Comment> comment) implements Reply, PersonalData {

        /** Checks every part is present. */
        public RatingReply {
            Objects.requireNonNull(rating, "rating");
            Objects.requireNonNull(comment, "comment");
        }

        /**
         * A form that names the rating and hides the comment.
         *
         * @return the reply without its text
         */
        @Override
        public String toString() {
            return "RatingReply[rating=" + rating.value() + ", comment=" + (comment.isPresent() ? "redacted" : "none")
                    + "]";
        }
    }

    /**
     * A reply that is neither an opt-out nor a rating, kept as a comment (FR-360).
     *
     * @param comment the reply as a comment
     */
    record TextReply(Comment comment) implements Reply, PersonalData {

        /** Checks the comment is present. */
        public TextReply {
            Objects.requireNonNull(comment, "comment");
        }

        /**
         * A form that hides the comment.
         *
         * @return {@code TextReply[redacted]}
         */
        @Override
        public String toString() {
            return "TextReply[redacted]";
        }
    }

    /** A reply with nothing visible: neither a rating nor an opt-out, and nothing to keep. */
    record BlankReply() implements Reply {}
}
