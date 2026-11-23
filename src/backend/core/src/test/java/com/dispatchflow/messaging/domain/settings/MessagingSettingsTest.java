package com.dispatchflow.messaging.domain.settings;

import static com.dispatchflow.messaging.domain.MessagingFixtures.COMPANY;
import static com.dispatchflow.messaging.domain.MessagingFixtures.DISPATCH;
import static com.dispatchflow.messaging.domain.MessagingFixtures.MOBILE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageLanguage;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import org.junit.jupiter.api.Test;

/** The company's messaging settings (FR-350, FR-375, FR-125, DL §4.6). */
class MessagingSettingsTest {

    @Test
    void GivenADeployment_WhenTheSettingsAreCreated_ThenMessagesAndTheRatingQuestionStartOff() {
        MessagingSettings settings = MessagingSettings.create(DISPATCH);

        assertThat(settings.snapshot())
                .isEqualTo(new MessagingSettingsSnapshot(null, Switch.OFF, Switch.OFF, DISPATCH));
        assertThat(settings.conditions(COMPANY, MessageLanguage.HEBREW, PhoneConsent.MAY_BE_MESSAGED)
                        .mayMessage())
                .isFalse();
    }

    @Test
    void GivenTheSettings_WhenEachIsChanged_ThenTheNewValuesAreKeptAndRestoreRoundTrips() {
        MessagingSettings settings = MessagingSettings.create(DISPATCH);
        DispatchPhone mobile = DispatchPhone.of(MOBILE);

        settings.changeMessages(Switch.ON);
        settings.changeRatingQuestion(Switch.ON);
        settings.changeDispatchPhone(mobile);

        MessagingSettingsSnapshot changed = settings.snapshot();
        assertThat(changed).isEqualTo(new MessagingSettingsSnapshot(null, Switch.ON, Switch.ON, mobile));
        assertThat(MessagingSettings.restore(changed).snapshot()).isEqualTo(changed);
        assertThat(settings.pendingEvents()).isEmpty();
    }

    @Test
    void GivenMessagesOn_WhenConditionsAreTakenForEachPhoneStanding_ThenOnlyAPhoneNotOptedOutMayBeMessaged() {
        MessagingSettings settings = MessagingSettings.create(DISPATCH);
        settings.changeMessages(Switch.ON);

        SendingConditions allowed = settings.conditions(COMPANY, MessageLanguage.ENGLISH, PhoneConsent.MAY_BE_MESSAGED);

        assertThat(allowed)
                .isEqualTo(new SendingConditions(
                        Switch.ON,
                        Switch.OFF,
                        DISPATCH,
                        COMPANY,
                        MessageLanguage.ENGLISH,
                        PhoneConsent.MAY_BE_MESSAGED));
        assertThat(allowed.mayMessage()).isTrue();
        assertThat(settings.conditions(COMPANY, MessageLanguage.ENGLISH, PhoneConsent.OPTED_OUT)
                        .mayMessage())
                .isFalse();
    }
}
