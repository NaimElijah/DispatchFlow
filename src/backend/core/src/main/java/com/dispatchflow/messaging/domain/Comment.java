package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.MessagingError.BlankComment;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TextKind;
import java.text.Normalizer;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A recipient's reply kept as a comment on the delivery (FR-360): plain text cut to 500
 * characters (NFR-203), under the kernel's text rule for a note ({@link PlainText}, {@link
 * TextKind#NOTE}; ADR-106).
 *
 * <p>The kernel refuses a note over its cap, or with a character it forbids; a comment is cut, and
 * each forbidden character becomes U+FFFD, because a reply is never the recipient's mistake to fix
 * and a refused one would be sent again for ever (NFR-201, ADR-108). Characters are counted in
 * Unicode code points, as the kernel counts them.
 *
 * <p>A comment can say anything about a person, so it is personal data: {@link #toString()} hides
 * it, and no error or event carries it (NFR-193).
 */
public final class Comment implements PersonalData {

    /** The most code points a comment keeps (NFR-203); the kernel's cap for a note. */
    public static final int MAXIMUM_LENGTH = TextKind.NOTE.maxLength();

    private static final String CARRIAGE_RETURN_LINE_FEED = "\r\n";
    private static final String LINE_FEED = "\n";
    private static final String CARRIAGE_RETURN = "\r";
    private static final int LINE_FEED_CODE_POINT = '\n';
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    private static final int LEFT_TO_RIGHT_EMBEDDING = 0x202A;
    private static final int RIGHT_TO_LEFT_OVERRIDE = 0x202E;
    private static final int LEFT_TO_RIGHT_ISOLATE = 0x2066;
    private static final int POP_DIRECTIONAL_ISOLATE = 0x2069;

    private final String text;

    private Comment(String text) {
        this.text = text;
    }

    /**
     * Keeps a reply as a comment, cut to 500 code points (NFR-203, ADR-108):
     *
     * <ol>
     *   <li>line ends become line feeds, so a Windows line end counts as one character;
     *   <li>the ends are trimmed as the kernel trims them, so leading blanks don't use up the 500;
     *   <li>the text is cut at a code point, so no surrogate pair is split, then normalized to NFC
     *       and cut again, because NFC turns some characters into two, such as a Hebrew letter with
     *       its dot written as one presentation form;
     *   <li>each code point the kernel refuses in a note becomes U+FFFD: a control other than a line
     *       feed, a bidi embedding, override or isolate control, an unpaired surrogate, and a
     *       private-use or unassigned code point, such as an emoji newer than the JDK's Unicode;
     *   <li>the kernel's rule for a note then checks it.
     * </ol>
     *
     * @param raw the reply as the provider delivered it
     * @return the comment
     * @throws RuleViolation with {@link BlankComment} when nothing visible is left
     */
    public static Comment cutFrom(String raw) {
        Objects.requireNonNull(raw, "raw");
        String lines = raw.replace(CARRIAGE_RETURN_LINE_FEED, LINE_FEED).replace(CARRIAGE_RETURN, LINE_FEED);
        String composed = Normalizer.normalize(firstCodePoints(ReplyText.trimmed(lines)), Normalizer.Form.NFC);
        PlainText plain = PlainText.of(TextKind.NOTE, withoutForbidden(firstCodePoints(composed)));
        if (plain.isEmpty()) {
            throw new RuleViolation(new BlankComment());
        }
        return new Comment(plain.text());
    }

    /**
     * Rebuilds a stored comment. It is checked, not cut: a stored comment over the cap is damaged.
     *
     * @param stored the comment as stored
     * @return the comment
     * @throws StoredStateInvalid when the kernel's rule refuses it or nothing visible is left
     */
    public static Comment ofStored(String stored) {
        PlainText plain = PlainText.ofStored(TextKind.NOTE, stored);
        if (plain.isEmpty()) {
            throw new StoredStateInvalid(Comment.class, "a stored comment is blank");
        }
        return new Comment(plain.text());
    }

    /**
     * The comment's text.
     *
     * @return the text in the kernel's normal form, never empty, at most 500 code points
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Comment that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the comment, which is personal data.
     *
     * @return {@code Comment[redacted]}
     */
    @Override
    public String toString() {
        return "Comment[redacted]";
    }

    private static String withoutForbidden(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        text.codePoints()
                .map(codePoint -> isForbiddenInANote(codePoint) ? REPLACEMENT_CHARACTER : codePoint)
                .forEach(kept::appendCodePoint);
        return kept.toString();
    }

    /**
     * The kernel's refusals for a note (ADR-106), which {@code PlainText} keeps private. {@code
     * ReplyTest}'s check that a comment with every kind of refused character is kept holds the two
     * in step.
     */
    private static boolean isForbiddenInANote(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL -> codePoint != LINE_FEED_CODE_POINT;
            case Character.FORMAT ->
                (codePoint >= LEFT_TO_RIGHT_EMBEDDING && codePoint <= RIGHT_TO_LEFT_OVERRIDE)
                        || (codePoint >= LEFT_TO_RIGHT_ISOLATE && codePoint <= POP_DIRECTIONAL_ISOLATE);
            case Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED -> true;
            default -> false;
        };
    }

    private static String firstCodePoints(String text) {
        if (text.codePointCount(0, text.length()) <= MAXIMUM_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAXIMUM_LENGTH));
    }
}
