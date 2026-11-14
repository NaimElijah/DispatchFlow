package com.dispatchflow.messaging.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.MessagingError.BlankComment;
import com.dispatchflow.messaging.domain.MessagingError.RatingOutOfRange;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a recipient's reply is read (FR-363, FR-376, FR-360, NFR-203; ADR-108): an opt-out word, a
 * rating with or without a comment, a comment, or nothing.
 */
class ReplyTest {

    // --- ratings (FR-376)

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void GivenAWholeNumberAtTheEndsOfTheRange_WhenARatingIsMade_ThenItHoldsTheNumber(int number) {
        assertThat(Rating.of(number).value()).isEqualTo(number);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -1})
    void GivenANumberJustOutsideTheRange_WhenARatingIsMade_ThenRatingOutOfRange(int number) {
        assertRefused(() -> Rating.of(number), new RatingOutOfRange(number));
    }

    @Test
    void GivenABareRating_WhenParsed_ThenItIsARatingWithNoComment() {
        assertThat(Reply.parse("8")).isEqualTo(new Reply.RatingReply(Rating.of(8), Optional.empty()));
        assertThat(Reply.parse("  10 \n")).isEqualTo(new Reply.RatingReply(Rating.of(10), Optional.empty()));
    }

    @Test
    void GivenARatingFollowedByWords_WhenParsed_ThenItIsTheRatingAndTheWholeReplyIsItsComment() {
        assertThat(Reply.parse("8 thanks"))
                .isEqualTo(new Reply.RatingReply(Rating.of(8), Optional.of(Comment.cutFrom("8 thanks"))));
    }

    @Test
    void GivenARatingFollowedByAPeriodWithNoDigit_WhenParsed_ThenItIsARatingWithThatComment() {
        assertThat(Reply.parse("9."))
                .isEqualTo(new Reply.RatingReply(Rating.of(9), Optional.of(Comment.cutFrom("9."))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "11", "07", "1.5", "10.5", "100", "٨", "great 9", "-3"})
    void GivenTextThatIsNotARatingUnderFr376_WhenParsed_ThenItIsACommentOnly(String text) {
        assertThat(Reply.parse(text)).isEqualTo(new Reply.TextReply(Comment.cutFrom(text)));
    }

    // --- opt-out words (FR-363)

    @ParameterizedTest
    @ValueSource(
            strings = {
                "הסר",
                "הסרה",
                "להסרה",
                "STOP",
                "stop",
                "Stop!",
                "  הסר.",
                "\"STOP\" please",
                "S.T.O.P",
                "STOP\uD83D\uDE21",
                "הסר\uD83D\uDE4F",
                "STOP\u200F please",
                "\uFF33\uFF34\uFF2F\uFF30",
                "stop\uD83D\uDC4D\uD83C\uDFFD"
            })
    void GivenAReplyWhoseFirstTokenIsAnOptOutWordOnceNormalizedAndStripped_WhenParsed_ThenItIsAnOptOut(String text) {
        assertThat(Reply.parse(text)).isEqualTo(new Reply.OptOutWord());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STOPS", "please stop", "stop,please", "הסרת", "5 stop", "STOP1"})
    void GivenAReplyWhoseFirstTokenIsNotAnOptOutWord_WhenParsed_ThenItIsNoOptOut(String text) {
        assertThat(Reply.parse(text)).isNotEqualTo(new Reply.OptOutWord());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t ", "\uFE0F"})
    void GivenAReplyWithNothingVisible_WhenParsed_ThenItIsBlank(String text) {
        assertThat(Reply.parse(text)).isEqualTo(new Reply.BlankReply());
    }

    // --- comments (FR-360, NFR-203, ADR-108)

    @Test
    void GivenACommentOfExactlyTheCap_WhenCut_ThenItIsKeptWhole() {
        String text = "א".repeat(500);

        assertThat(Comment.cutFrom(text).text()).isEqualTo(text);
    }

    @Test
    void GivenACommentOneCodePointPastTheCap_WhenCut_ThenItKeepsTheFirst500() {
        assertThat(Comment.cutFrom("א".repeat(500) + "ב").text()).isEqualTo("א".repeat(500));
    }

    @Test
    void GivenACommentPastTheCapEndingInAPairOfSurrogates_WhenCut_ThenNoPairIsSplit() {
        String emoji = "\uD83D\uDE00";
        String text = "a".repeat(499) + emoji + emoji;

        assertThat(Comment.cutFrom(text).text()).isEqualTo("a".repeat(499) + emoji);
    }

    @Test
    void GivenACommentOfWindowsLineEnds_WhenCut_ThenEachLineEndCountsAsOneLineFeed() {
        String line = "x".repeat(9) + "\r\n";
        String text = line.repeat(50) + "tail";

        assertThat(Comment.cutFrom(text).text()).isEqualTo(("x".repeat(9) + "\n").repeat(49) + "x".repeat(9));
        assertThat(Comment.cutFrom("a\rb\r\nc").text()).isEqualTo("a\nb\nc");
    }

    @Test
    void GivenACommentOfHebrewPresentationFormsThatNfcDecomposes_WhenCut_ThenItIsCutToTheCapRatherThanRefused() {
        // U+FB2A (shin with shin dot) is two code points in NFC, so 500 of them become 1,000.
        String text = "\uFB2A".repeat(500);

        assertThat(Comment.cutFrom(text).text()).isEqualTo("ש\u05C1".repeat(250));
    }

    @Test
    void GivenACommentThatIsOnlySpaces_WhenCut_ThenBlankComment() {
        assertRefused(() -> Comment.cutFrom("  \n "), new BlankComment());
    }

    @Test
    void GivenATextReplyWithABidiOverride_WhenParsed_ThenTheOverrideIsReplacedAndTheCommentKept() {
        assertThat(Reply.parse("ok \u202E gone")).isEqualTo(new Reply.TextReply(Comment.ofStored("ok \uFFFD gone")));
    }

    @Test
    void GivenARatingWithAnEmojiNewerThanTheJdksUnicode_WhenParsed_ThenTheRatingAndTheCommentSurvive() {
        // U+1FAE9 (face with bags under the eyes) is Unicode 16, unassigned in JDK 21.
        assertThat(Reply.parse("10 \uD83E\uDEE9"))
                .isEqualTo(new Reply.RatingReply(Rating.of(10), Optional.of(Comment.ofStored("10 \uFFFD"))));
    }

    @Test
    void GivenARatingWithATab_WhenParsed_ThenTheRatingAndTheCommentSurvive() {
        assertThat(Reply.parse("9\tthanks"))
                .isEqualTo(new Reply.RatingReply(Rating.of(9), Optional.of(Comment.ofStored("9\uFFFDthanks"))));
        assertThat(Reply.parse("9 \u202E gone"))
                .isEqualTo(new Reply.RatingReply(Rating.of(9), Optional.of(Comment.ofStored("9 \uFFFD gone"))));
    }

    @Test
    void GivenACommentWithEveryKindTheKernelRefusesInANote_WhenCut_ThenEachBecomesAReplacementAndNoneIsRefused() {
        // A control, a bidi isolate, a private-use, an unassigned and an unpaired surrogate code point.
        String text = "a\u0007b\u2066c\uE000d\uD83E\uDEE9e\uD800f";

        assertThat(Comment.cutFrom(text).text()).isEqualTo("a\uFFFDb\uFFFDc\uFFFDd\uFFFDe\uFFFDf");
    }

    @Test
    void GivenARatingReplyThatStartsWith600Blanks_WhenParsed_ThenItIsTheRatingAndItsComment() {
        assertThat(Reply.parse(" ".repeat(600) + "8 ok"))
                .isEqualTo(new Reply.RatingReply(Rating.of(8), Optional.of(Comment.ofStored("8 ok"))));
    }

    @Test
    void GivenACommentAfter600Blanks_WhenCut_ThenItKeeps500VisibleCharacters() {
        assertThat(Comment.cutFrom(" \n".repeat(300) + "a".repeat(501)).text()).isEqualTo("a".repeat(500));
    }

    @Test
    void GivenAStoredCommentOverTheCap_WhenRestored_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> Comment.ofStored("a".repeat(501))).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> Comment.ofStored(" ")).isInstanceOf(StoredStateInvalid.class);
        assertThat(Comment.ofStored("fine").text()).isEqualTo("fine");
    }

    @Test
    void GivenACommentAndARatingReply_WhenPrinted_ThenTheirTextIsHidden() {
        assertThat(Comment.cutFrom("call me on 0523456789").toString()).doesNotContain("0523456789");
        assertThat(Reply.parse("8 call 0523456789").toString()).doesNotContain("0523456789");
        assertThat(Reply.parse("call 0523456789").toString()).doesNotContain("0523456789");
    }
}
