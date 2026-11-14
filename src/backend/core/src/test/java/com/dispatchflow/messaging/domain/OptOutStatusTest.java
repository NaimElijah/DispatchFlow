package com.dispatchflow.messaging.domain;

import static com.dispatchflow.messaging.domain.MessagingFixtures.TEN;
import static com.dispatchflow.messaging.domain.MessagingFixtures.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A phone's opt-out history (FR-363, FR-380, NFR-202; ADR-108): the latest entry wins, a reply
 * opts out at once and is confirmed once, and a dispatcher records or removes an opt-out.
 */
class OptOutStatusTest {

    private static final PhoneHash PHONE = new PhoneHash("hmac:aaaa");
    private static final PhoneHash OTHER = new PhoneHash("hmac:bbbb");
    private static final AccountId DISPATCHER = AccountId.of(uuid(900));
    private static final Instant LATER = TEN.plusSeconds(3_600);

    private static OptOutStatus history(OptOutRecord... entries) {
        return OptOutStatus.of(PHONE, List.of(entries));
    }

    @Test
    void GivenNoHistory_WhenRead_ThenThePhoneMayBeMessaged() {
        assertThat(history().consent()).isEqualTo(PhoneConsent.MAY_BE_MESSAGED);
    }

    @Test
    void GivenAnOptOutThenADispatchersOptIn_WhenRead_ThenTheLatestWinsAndThePhoneMayBeMessaged() {
        OptOutStatus status =
                history(OptOutRecord.byReply(PHONE, TEN), OptOutRecord.removedBy(PHONE, DISPATCHER, LATER));

        assertThat(status.consent()).isEqualTo(PhoneConsent.MAY_BE_MESSAGED);
    }

    @Test
    void GivenAnOptOutTimedBeforeAnOptInButAppendedAfterIt_WhenRead_ThenTheEntryAppendedLastWins() {
        OptOutStatus status =
                history(OptOutRecord.removedBy(PHONE, DISPATCHER, LATER), OptOutRecord.byReply(PHONE, TEN));

        assertThat(status.consent()).isEqualTo(PhoneConsent.OPTED_OUT);
    }

    @Test
    void
            GivenAnOptOutAt0800AndAnOptInAt1000_WhenAStopTimed0900IsProcessedAfterBoth_ThenThePhoneIsOptedOutAndConfirmed() {
        Instant eight = Instant.parse("2027-06-15T05:00:00Z");
        Instant nine = Instant.parse("2027-06-15T06:00:00Z");
        OptOutRecord optOut = OptOutRecord.byReply(PHONE, eight);
        OptOutRecord optIn = OptOutRecord.removedBy(PHONE, DISPATCHER, TEN);

        ReplyOptOut lateStop = history(optOut, optIn).optOutByReply(nine, Optional.of(eight), Switch.ON);

        assertThat(lateStop).isEqualTo(new ReplyOptOut(Optional.of(OptOutRecord.byReply(PHONE, nine)), true));
        assertThat(history(optOut, optIn, lateStop.entry().orElseThrow()).consent())
                .isEqualTo(PhoneConsent.OPTED_OUT);
    }

    @Test
    void GivenTwoEntriesAtTheSameTime_WhenRead_ThenTheOneAppendedLastWins() {
        OptOutStatus status = history(OptOutRecord.byReply(PHONE, TEN), OptOutRecord.removedBy(PHONE, DISPATCHER, TEN));

        assertThat(status.consent()).isEqualTo(PhoneConsent.MAY_BE_MESSAGED);
    }

    @Test
    void GivenAHistoryWithAnotherPhonesEntry_WhenRead_ThenItFailsAsABug() {
        assertThatThrownBy(() -> history(OptOutRecord.byReply(OTHER, TEN))).isInstanceOf(AssertionError.class);
    }

    @Test
    void GivenAPhoneMessaged48HoursBeforeItsOptOut_WhenItRepliesStop_ThenItOptsOutAndGetsTheConfirmation() {
        ReplyOptOut result = history().optOutByReply(LATER, Optional.of(LATER.minusSeconds(48 * 3_600)), Switch.ON);

        assertThat(result).isEqualTo(new ReplyOptOut(Optional.of(OptOutRecord.byReply(PHONE, LATER)), true));
    }

    @Test
    void GivenAPhoneMessagedJustOver48HoursBefore_WhenItRepliesStop_ThenItOptsOutWithNoConfirmation() {
        ReplyOptOut result = history().optOutByReply(LATER, Optional.of(LATER.minusSeconds(48 * 3_600 + 1)), Switch.ON);

        assertThat(result).isEqualTo(new ReplyOptOut(Optional.of(OptOutRecord.byReply(PHONE, LATER)), false));
    }

    @Test
    void GivenAPhoneNeverMessaged_WhenItRepliesStop_ThenItOptsOutWithNoConfirmation() {
        assertThat(history().optOutByReply(LATER, Optional.empty(), Switch.ON).confirmationDue())
                .isFalse();
    }

    @Test
    void GivenMessagesTurnedOff_WhenARecentlyMessagedPhoneRepliesStop_ThenItOptsOutWithNoConfirmation() {
        ReplyOptOut result = history().optOutByReply(LATER, Optional.of(TEN), Switch.OFF);

        assertThat(result).isEqualTo(new ReplyOptOut(Optional.of(OptOutRecord.byReply(PHONE, LATER)), false));
    }

    @Test
    void GivenAPhoneAlreadyOptedOut_WhenItRepliesStopAgain_ThenNothingIsAppendedAndNothingConfirmed() {
        ReplyOptOut result =
                history(OptOutRecord.byReply(PHONE, TEN)).optOutByReply(LATER, Optional.of(TEN), Switch.ON);

        assertThat(result).isEqualTo(new ReplyOptOut(Optional.empty(), false));
    }

    @Test
    void GivenAPhoneNotOptedOut_WhenADispatcherRecordsAnOptOut_ThenItIsAppendedWithWhoAndWhen() {
        Optional<OptOutRecord> entry = history().recordByDispatcher(DISPATCHER, LATER);

        assertThat(entry).contains(OptOutRecord.recordedBy(PHONE, DISPATCHER, LATER));
        assertThat(entry.get().dispatcher()).contains(DISPATCHER);
        assertThat(history(OptOutRecord.byReply(PHONE, TEN)).recordByDispatcher(DISPATCHER, LATER))
                .isEmpty();
    }

    @Test
    void GivenAnOptedOutPhone_WhenADispatcherRemovesTheOptOut_ThenAnOptInIsAppendedWithWhoAndWhen() {
        Optional<OptOutRecord> entry = history(OptOutRecord.byReply(PHONE, TEN)).removeByDispatcher(DISPATCHER, LATER);

        assertThat(entry).contains(OptOutRecord.removedBy(PHONE, DISPATCHER, LATER));
        assertThat(entry.get().change()).isEqualTo(OptOutRecord.Change.OPTED_IN);
        assertThat(history().removeByDispatcher(DISPATCHER, LATER)).isEmpty();
    }

    @Test
    void GivenAStoredOptInWithNoDispatcher_WhenRestored_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> OptOutRecord.ofStored(PHONE, OptOutRecord.Change.OPTED_IN, TEN, Optional.empty()))
                .isInstanceOf(StoredStateInvalid.class);
        assertThat(OptOutRecord.ofStored(PHONE, OptOutRecord.Change.OPTED_OUT, TEN, Optional.empty()))
                .isEqualTo(OptOutRecord.byReply(PHONE, TEN));
    }

    @Test
    void GivenAnEntryAndAStatus_WhenPrinted_ThenNeitherShowsTheHash() {
        assertThat(OptOutRecord.byReply(PHONE, TEN).toString()).doesNotContain("aaaa");
        assertThat(history(OptOutRecord.byReply(PHONE, TEN)).toString()).doesNotContain("aaaa");
        assertThat(PHONE.toString()).doesNotContain("aaaa");
    }
}
