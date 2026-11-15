package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.LANDLINE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.MessagingError.DispatchPhoneNotIsraeli;
import com.dispatchflow.messaging.domain.MessagingError.DispatchPhoneTooLong;
import com.dispatchflow.messaging.domain.MessagingError.UnusableCompanyName;
import com.dispatchflow.messaging.domain.message.DailySendCap;
import com.dispatchflow.messaging.domain.message.MessageState;
import com.dispatchflow.messaging.domain.message.MessageState.DropCause;
import com.dispatchflow.messaging.domain.message.ProviderReference;
import com.dispatchflow.messaging.domain.message.RecipientPhone;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Messaging's values (DL §3.3): the company name, the dispatch phone, quarter hours, the cap and the phone. */
class MessagingValuesTest {

    // --- the company name (FR-349, FR-382)

    @Test
    void GivenANameOf19Characters_WhenRead_ThenItIsKeptTrimmed() {
        assertThat(CompanyName.of("  Northern Deliveries ").value()).isEqualTo("Northern Deliveries");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Northern Deliveries!", "   ", ""})
    void GivenANameOf20CharactersOrBlank_WhenRead_ThenUnusableCompanyName(String name) {
        assertRefused(() -> CompanyName.of(name), new UnusableCompanyName(19));
    }

    // --- the dispatch phone (FR-382, ADR-108)

    @Test
    void GivenAnIsraeliMobile_WhenTakenAsTheDispatchPhone_ThenMessagesWriteItAsNationalDigits() {
        assertThat(DispatchPhone.of(MOBILE).national()).isEqualTo("0523456789");
        assertThat(DispatchPhone.of(LANDLINE).national()).isEqualTo("031234567");
    }

    @Test
    void GivenAnIsraeliNumberWhoseNationalFormIs11Characters_WhenTakenAsTheDispatchPhone_ThenItIsAccepted() {
        // An Israeli voicemail number, 151-1 234-0000.
        assertThat(DispatchPhone.of(PhoneNumber.parse("+97215112340000")).national())
                .isEqualTo("15112340000");
    }

    @Test
    void GivenAnIsraeliNumberWhoseNationalFormIs12Characters_WhenTakenAsTheDispatchPhone_ThenDispatchPhoneTooLong() {
        assertRefused(() -> DispatchPhone.of(PhoneNumber.parse("+972151234567890")), new DispatchPhoneTooLong(11));
    }

    @Test
    void GivenANumberOutsideIsrael_WhenTakenAsTheDispatchPhone_ThenDispatchPhoneNotIsraeli() {
        assertRefused(() -> DispatchPhone.of(PhoneNumber.parse("+1 202 555 0143")), new DispatchPhoneNotIsraeli());
        assertRefused(() -> DispatchPhone.of(PhoneNumber.ofStored("+493012345678")), new DispatchPhoneNotIsraeli());
    }

    @Test
    void GivenAStoredDispatchPhone_WhenRestored_ThenItIsCheckedAgain() {
        assertThat(DispatchPhone.ofStored("+97235551234").national()).isEqualTo("035551234");
        assertThatThrownBy(() -> DispatchPhone.ofStored("+972151234567890")).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> DispatchPhone.ofStored("+493012345678")).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> DispatchPhone.ofStored("035551234")).isInstanceOf(StoredStateInvalid.class);
    }

    // --- quarter hours (FR-351, ADR-108)

    @Test
    void GivenAProjectionJustBelowHalfAQuarter_WhenRounded_ThenItGoesDown() {
        Instant projected = Instant.parse("2027-06-15T07:07:29.999Z");

        assertThat(QuarterHour.nearest(MessagingFixtures.projected(projected)).at())
                .isEqualTo(TEN);
    }

    @Test
    void GivenAProjectionAtHalfAQuarter_WhenRounded_ThenItGoesUp() {
        Instant projected = Instant.parse("2027-06-15T07:07:30Z");

        assertThat(QuarterHour.nearest(MessagingFixtures.projected(projected)).at())
                .isEqualTo(Instant.parse("2027-06-15T07:15:00Z"));
    }

    @Test
    void GivenAProjectionOnAQuarter_WhenRounded_ThenItStays() {
        assertThat(QuarterHour.nearest(MessagingFixtures.projected(TEN)).at()).isEqualTo(TEN);
    }

    @Test
    void GivenAQuarterHour_WhenItsWindowIsRead_ThenItRuns30MinutesEitherSideInIsraelTime() {
        QuarterHour ten = new QuarterHour(TEN);

        assertThat(ten.windowStart()).isEqualTo(LocalTime.of(9, 30));
        assertThat(ten.windowEnd()).isEqualTo(LocalTime.of(10, 30));
        assertThat(ten.distanceTo(MessagingFixtures.projected(TEN.minusSeconds(1_800))))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void GivenAWinterQuarterHour_WhenItsWindowIsRead_ThenItIsInIsraelStandardTime() {
        // 15 January 2027: Israel is UTC+2.
        QuarterHour ten = new QuarterHour(Instant.parse("2027-01-15T08:00:00Z"));

        assertThat(ten.windowStart()).isEqualTo(LocalTime.of(9, 30));
        assertThat(ten.windowEnd()).isEqualTo(LocalTime.of(10, 30));
    }

    @Test
    void GivenAProjectionOnTheDayTheClockMovesBack_WhenRoundedAndItsWindowRead_ThenItIsInWinterTime() {
        // Sunday 31 October 2027: the clock moved back from 02:00 to 01:00, so the day is UTC+2.
        QuarterHour arrival = QuarterHour.nearest(MessagingFixtures.projected(Instant.parse("2027-10-31T08:07:30Z")));

        assertThat(arrival.at()).isEqualTo(Instant.parse("2027-10-31T08:15:00Z"));
        assertThat(arrival.windowStart()).isEqualTo(LocalTime.of(9, 45));
        assertThat(arrival.windowEnd()).isEqualTo(LocalTime.of(10, 45));
    }

    @Test
    void GivenAStoredArrivalOffAQuarter_WhenRestored_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new QuarterHour(TEN.plusSeconds(60))).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> new QuarterHour(TEN.plusNanos(1))).isInstanceOf(StoredStateInvalid.class);
    }

    // --- the daily cap (NFR-196)

    @Test
    void GivenTheMessagesClaimedToday_WhenTheCapCountsThem_ThenEveryClaimThatMayHaveLeftCounts() {
        List<MessageState> claimedToday = List.of(
                new MessageState.Pending(TEN),
                new MessageState.Sending(TEN),
                new MessageState.Unknown(TEN),
                new MessageState.Accepted(TEN, new ProviderReference("p-1")),
                new MessageState.Dropped(TEN, DropCause.OUTCOME_UNKNOWN),
                new MessageState.Dropped(TEN, DropCause.STALE_WHILE_SENDING),
                new MessageState.Dropped(TEN, DropCause.STALE),
                new MessageState.Dropped(TEN, DropCause.REFUSED),
                new MessageState.Dropped(TEN, DropCause.OPTED_OUT));

        assertThat(DailySendCap.counting(claimedToday)).isEqualTo(new DailySendCap(5));
    }

    @Test
    void GivenTheDaysClaimedMessages_WhenTheCapIsRead_ThenTheThousandthIsTheLast() {
        assertThat(new DailySendCap(0).hasRoom()).isTrue();
        assertThat(new DailySendCap(999).hasRoom()).isTrue();
        assertThat(new DailySendCap(1_000).hasRoom()).isFalse();
        assertThat(new DailySendCap(1_001).hasRoom()).isFalse();
        assertThatThrownBy(() -> new DailySendCap(-1)).isInstanceOf(StoredStateInvalid.class);
    }

    // --- the recipient's phone (FR-348, FR-378)

    @Test
    void GivenAnOrderRecipientPhoneAndACustomerPhone_WhenChosen_ThenTheRecipientsComesFirst() {
        assertThat(RecipientPhone.choose(Optional.of(MOBILE), Optional.of(LANDLINE)))
                .isEqualTo(new RecipientPhone.Known(MOBILE));
        assertThat(RecipientPhone.choose(Optional.empty(), Optional.of(LANDLINE)))
                .isEqualTo(new RecipientPhone.Known(LANDLINE));
        assertThat(RecipientPhone.choose(Optional.empty(), Optional.empty())).isEqualTo(new RecipientPhone.None());
    }

    @Test
    void GivenEachKindOfPhone_WhenAskedWhetherItTakesMessages_ThenOnlyAnIsraeliMobileDoes() {
        assertThat(new RecipientPhone.Known(MOBILE).takesMessages()).isTrue();
        assertThat(new RecipientPhone.Known(LANDLINE).takesMessages()).isFalse();
        assertThat(new RecipientPhone.Known(PhoneNumber.parse("+1 202 555 0143")).takesMessages())
                .isFalse();
        assertThat(new RecipientPhone.None().takesMessages()).isFalse();
        assertThat(new RecipientPhone.Known(MOBILE).toString()).doesNotContain("345");
    }
}
