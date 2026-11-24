package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A message's text: the company's name, then the fixed wording of its type in the company's
 * language, within one SMS of 70 characters (FR-349, FR-382, DL §3.3, §3.7).
 *
 * <p>The Hebrew is ADR-031's wording as written. The English is ADR-108's, as decided.
 * With a company name of 19 characters and a dispatch phone of 11, the longest
 * text is exactly 70, so {@link #of} never meets a longer one; the length is counted in UTF-16
 * units, the units an SMS is counted in.
 *
 * <p>A message to a recipient is personal data (NFR-193): {@link #toString()} hides it.
 */
public final class MessageText implements PersonalData {

    /** The most characters one SMS of Unicode text holds (FR-382). */
    public static final int MAXIMUM_LENGTH = 70;

    private static final String AFTER_NAME = ": ";
    private static final String WINDOW_DASH = "–";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final String text;

    private MessageText(String text) {
        this.text = text;
    }

    /**
     * Writes a message.
     *
     * @param company the company's name, which starts the text (FR-349)
     * @param language the company's default language (FR-349)
     * @param wording the message's fixed text and the facts it gives
     * @return the text
     * @throws AssertionError when the text is longer than 70 characters, which the limits on the
     *     company name and the dispatch phone rule out: a bug in a wording, not a refusal
     */
    public static MessageText of(CompanyName company, MessageLanguage language, Wording wording) {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(wording, "wording");
        String body = switch (Objects.requireNonNull(language, "language")) {
            case HEBREW -> hebrew(wording);
            case ENGLISH -> english(wording);
        };
        String text = company.value() + AFTER_NAME + body;
        if (text.length() > MAXIMUM_LENGTH) {
            throw new AssertionError("a message's wording must fit one SMS at the FR-382 limits");
        }
        return new MessageText(text);
    }

    /**
     * Rebuilds a stored text.
     *
     * @param stored the text as stored
     * @return the text
     * @throws StoredStateInvalid when it is empty or longer than 70 characters
     */
    public static MessageText ofStored(String stored) {
        Objects.requireNonNull(stored, "stored");
        if (stored.isEmpty() || stored.length() > MAXIMUM_LENGTH) {
            throw new StoredStateInvalid(MessageText.class, "a message's text is 1 to 70 characters");
        }
        return new MessageText(stored);
    }

    /**
     * The text the provider sends.
     *
     * @return the text, at most 70 characters
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MessageText that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, which is personal data (NFR-193).
     *
     * @return {@code MessageText[redacted]}
     */
    @Override
    public String toString() {
        return "MessageText[redacted]";
    }

    /** ADR-031's wording, as written. */
    private static String hebrew(Wording wording) {
        return switch (wording) {
            case Wording.OutForDelivery(QuarterHour arrival) -> "המשלוח בדרך ויגיע היום בין " + window(arrival);
            case Wording.ArrivingSoon arrivingSoon -> "הנהג יגיע אליך בעוד כ-15 דקות";
            case Wording.TimeChanged(QuarterHour arrival) -> "זמן ההגעה עודכן: היום בין " + window(arrival);
            case Wording.DeliveryChanged(DispatchPhone phone) -> "חל שינוי במשלוח. לפרטים: " + phone.national();
            case Wording.MissedYou(DispatchPhone phone) -> "לא הצלחנו למסור את המשלוח. לפרטים: " + phone.national();
            case Wording.Delivered(boolean leftAtDoor, boolean asksForRating) ->
                "המשלוח נמסר" + (leftAtDoor ? " (ליד הדלת)." : ".")
                        + (asksForRating ? " איך היה? השב/י במספר 1–10" : "");
            case Wording.Answering(ReplyMessage message) ->
                switch (message) {
                    case THANK_YOU -> "תודה על המשוב!";
                    case FORMAT_REMINDER -> "נא להשיב במספר בין 1 ל-10 בלבד";
                    case OPT_OUT_CONFIRMATION -> "הוסרת, ולא יישלחו אליך עוד הודעות";
                };
        };
    }

    /** ADR-108's English wording, as decided. */
    private static String english(Wording wording) {
        return switch (wording) {
            case Wording.OutForDelivery(QuarterHour arrival) ->
                "Your delivery arrives today between " + window(arrival);
            case Wording.ArrivingSoon arrivingSoon -> "The driver will reach you in about 15 minutes";
            case Wording.TimeChanged(QuarterHour arrival) -> "Arrival time updated: today between " + window(arrival);
            case Wording.DeliveryChanged(DispatchPhone phone) ->
                "Your delivery has changed. Details: " + phone.national();
            case Wording.MissedYou(DispatchPhone phone) -> "We could not deliver it. Details: " + phone.national();
            case Wording.Delivered(boolean leftAtDoor, boolean asksForRating) ->
                "Delivered" + (leftAtDoor ? " (at the door)." : ".") + (asksForRating ? " How was it? Reply 1–10" : "");
            case Wording.Answering(ReplyMessage message) ->
                switch (message) {
                    case THANK_YOU -> "Thank you for your feedback!";
                    case FORMAT_REMINDER -> "Please reply with a number from 1 to 10 only";
                    case OPT_OUT_CONFIRMATION -> "You are removed and will get no more messages";
                };
        };
    }

    private static String window(QuarterHour arrival) {
        return clock(arrival.windowStart()) + WINDOW_DASH + clock(arrival.windowEnd());
    }

    private static String clock(LocalTime time) {
        return CLOCK.format(time);
    }
}
