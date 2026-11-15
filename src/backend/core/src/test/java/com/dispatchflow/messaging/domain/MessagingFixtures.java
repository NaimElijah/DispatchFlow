package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.message.RecipientPhone;
import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Values every Messaging test shares. Times are on 15 June 2027, when Israel is UTC+3. */
public final class MessagingFixtures {

    /** 10:00 in Israel. */
    public static final Instant TEN = Instant.parse("2027-06-15T07:00:00Z");

    public static final StopId STOP = StopId.of(uuid(1));
    public static final PhoneNumber MOBILE = PhoneNumber.parse("052-345-6789");
    public static final PhoneNumber LANDLINE = PhoneNumber.parse("03-123-4567");
    public static final RecipientPhone TO_MOBILE = new RecipientPhone.Known(MOBILE);
    public static final PhoneNumber OTHER_MOBILE = PhoneNumber.parse("054-345-6789");
    public static final RecipientPhone TO_OTHER_MOBILE = new RecipientPhone.Known(OTHER_MOBILE);
    public static final CompanyName COMPANY = CompanyName.of("Shefa Deliveries");
    public static final DispatchPhone DISPATCH = DispatchPhone.of(PhoneNumber.parse("03-555-1234"));

    private MessagingFixtures() {}

    public static UUID uuid(int n) {
        return UUID.fromString("0190a5e2-0000-7000-c000-%012d".formatted(n));
    }

    public static OutgoingMessageId messageId(int n) {
        return OutgoingMessageId.of(uuid(100 + n));
    }

    public static FreshIds freshIds(int... ns) {
        return FreshIds.of(Arrays.stream(ns).mapToObj(n -> messageId(n).value()).toList());
    }

    public static Instant at(int minutesAfterTen) {
        return TEN.plusSeconds(60L * minutesAfterTen);
    }

    public static ProjectedArrival projected(Instant at) {
        return new ProjectedArrival(at);
    }

    public static QuarterHour quarter(int minutesAfterTen) {
        return new QuarterHour(at(minutesAfterTen));
    }

    /** Messages on, the rating question off, Hebrew, the phone not opted out. */
    public static SendingConditions on() {
        return conditions(Switch.ON, Switch.OFF, PhoneConsent.MAY_BE_MESSAGED);
    }

    public static SendingConditions conditions(Switch messages, Switch rating, PhoneConsent consent) {
        return new SendingConditions(messages, rating, DISPATCH, COMPANY, MessageLanguage.HEBREW, consent);
    }

    public static MessageText text(Wording wording) {
        return on().write(wording);
    }
}
