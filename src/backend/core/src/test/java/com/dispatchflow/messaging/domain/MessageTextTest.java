package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.DISPATCH;
import static com.dispatchflow.messaging.domain.MessagingFixtures.quarter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every message's fixed text (FR-349, FR-382): ADR-031's Hebrew as written, ADR-108's English
 * draft, and the 70-character limit at the longest company name and dispatch phone.
 */
class MessageTextTest {

    private static final CompanyName ACME = CompanyName.of("Acme");

    /** 19 characters, the most FR-382 allows. */
    private static final CompanyName LONGEST_NAME = CompanyName.of("Northern Deliveries");

    /** A national form of 10 characters, the longest an Israeli number has. */
    private static final DispatchPhone LONGEST_PHONE = DispatchPhone.of(PhoneNumber.parse("052-345-6789"));

    static Stream<Arguments> wordings() {
        return Stream.of(
                Arguments.of(
                        new Wording.OutForDelivery(quarter(60)),
                        "Acme: המשלוח בדרך ויגיע היום בין 10:30–11:30",
                        "Acme: Your delivery arrives today between 10:30–11:30"),
                Arguments.of(
                        new Wording.ArrivingSoon(),
                        "Acme: הנהג יגיע אליך בעוד כ-15 דקות",
                        "Acme: The driver will reach you in about 15 minutes"),
                Arguments.of(
                        new Wording.TimeChanged(quarter(150)),
                        "Acme: זמן ההגעה עודכן: היום בין 12:00–13:00",
                        "Acme: Arrival time updated: today between 12:00–13:00"),
                Arguments.of(
                        new Wording.DeliveryChanged(DISPATCH),
                        "Acme: חל שינוי במשלוח. לפרטים: 035551234",
                        "Acme: Your delivery has changed. Details: 035551234"),
                Arguments.of(
                        new Wording.MissedYou(DISPATCH),
                        "Acme: לא הצלחנו למסור את המשלוח. לפרטים: 035551234",
                        "Acme: We could not deliver it. Details: 035551234"),
                Arguments.of(new Wording.Delivered(false, false), "Acme: המשלוח נמסר.", "Acme: Delivered."),
                Arguments.of(
                        new Wording.Delivered(true, false),
                        "Acme: המשלוח נמסר (ליד הדלת).",
                        "Acme: Delivered (at the door)."),
                Arguments.of(
                        new Wording.Delivered(false, true),
                        "Acme: המשלוח נמסר. איך היה? השב/י במספר 1–10",
                        "Acme: Delivered. How was it? Reply 1–10"),
                Arguments.of(
                        new Wording.Delivered(true, true),
                        "Acme: המשלוח נמסר (ליד הדלת). איך היה? השב/י במספר 1–10",
                        "Acme: Delivered (at the door). How was it? Reply 1–10"),
                Arguments.of(
                        new Wording.Answering(ReplyMessage.THANK_YOU),
                        "Acme: תודה על המשוב!",
                        "Acme: Thank you for your feedback!"),
                Arguments.of(
                        new Wording.Answering(ReplyMessage.FORMAT_REMINDER),
                        "Acme: נא להשיב במספר בין 1 ל-10 בלבד",
                        "Acme: Please reply with a number from 1 to 10 only"),
                Arguments.of(
                        new Wording.Answering(ReplyMessage.OPT_OUT_CONFIRMATION),
                        "Acme: הוסרת, ולא יישלחו אליך עוד הודעות",
                        "Acme: You are removed and will get no more messages"));
    }

    @ParameterizedTest
    @MethodSource("wordings")
    void GivenEachWording_WhenWrittenInEachLanguage_ThenItIsTheFixedTextAfterTheCompanyName(
            Wording wording, String hebrew, String english) {
        assertThat(MessageText.of(ACME, MessageLanguage.HEBREW, wording).text()).isEqualTo(hebrew);
        assertThat(MessageText.of(ACME, MessageLanguage.ENGLISH, wording).text())
                .isEqualTo(english);
    }

    @ParameterizedTest
    @MethodSource("wordings")
    void GivenTheLongestNameAndPhone_WhenEachWordingIsWritten_ThenItFitsOneSms(Wording wording) {
        Wording longest = switch (wording) {
            case Wording.DeliveryChanged changed -> new Wording.DeliveryChanged(LONGEST_PHONE);
            case Wording.MissedYou missed -> new Wording.MissedYou(LONGEST_PHONE);
            default -> wording;
        };
        for (MessageLanguage language : MessageLanguage.values()) {
            assertThat(MessageText.of(LONGEST_NAME, language, longest).text()).hasSizeLessThanOrEqualTo(70);
        }
    }

    @Test
    void GivenTheLongestNameAndAnElevenCharacterPhone_WhenTheLongestTextsAreWritten_ThenTheyFitAndOneIsExactly70() {
        DispatchPhone eleven = DispatchPhone.of(PhoneNumber.parse("+97215112340000"));

        assertThat(MessageText.of(LONGEST_NAME, MessageLanguage.HEBREW, new Wording.Delivered(true, true))
                        .text())
                .hasSize(70);
        assertThat(MessageText.of(LONGEST_NAME, MessageLanguage.HEBREW, new Wording.MissedYou(eleven))
                        .text())
                .hasSize(67);
        assertThat(MessageText.of(LONGEST_NAME, MessageLanguage.ENGLISH, new Wording.DeliveryChanged(eleven))
                        .text())
                .hasSize(68);
    }

    @Test
    void GivenAStoredText_WhenRestored_ThenItIsCheckedForLength() {
        assertThat(MessageText.ofStored("x".repeat(70)).text()).hasSize(70);
        assertThatThrownBy(() -> MessageText.ofStored("x".repeat(71))).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> MessageText.ofStored("")).isInstanceOf(StoredStateInvalid.class);
    }

    @Test
    void GivenAText_WhenPrinted_ThenItIsHidden() {
        assertThat(MessageText.of(ACME, MessageLanguage.HEBREW, new Wording.MissedYou(DISPATCH))
                        .toString())
                .doesNotContain("035551234")
                .doesNotContain("Acme");
    }
}
