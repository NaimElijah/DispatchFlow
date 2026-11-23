package com.dispatchflow.access.domain.companyprofile;

import static com.dispatchflow.shared.AsSaved.asSaved;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.Language;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompanyProfileTest {

    private static final CompanyName NAME = new CompanyName("מהיר משלוחים");
    private static final DefaultWindow NINE_TO_FIVE = new DefaultWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));
    private static final DefaultWindow EIGHT_TO_NOON = new DefaultWindow(LocalTime.of(8, 0), LocalTime.of(12, 0));

    /**
     * What a test sees of a profile, read through its getters.
     *
     * @param name the company's name
     * @param language the default language
     * @param defaultWindow the default time window
     * @param defaultServiceTime the default service time, once set
     */
    private record ProfileDescription(
            CompanyName name,
            Language language,
            DefaultWindow defaultWindow,
            Optional<DefaultServiceTime> defaultServiceTime) {

        static ProfileDescription of(CompanyProfile profile) {
            return new ProfileDescription(
                    profile.name(), profile.language(), profile.defaultWindow(), profile.defaultServiceTime());
        }
    }

    /** The profile as saved: Hebrew, 09:00 to 17:00, no default service time. */
    private static CompanyProfile aProfile() {
        return asSaved(CompanyProfile.create(NAME, Language.HEBREW, NINE_TO_FIVE));
    }

    @Test
    void GivenTheDeploymentsSettings_WhenTheProfileIsCreated_ThenItHoldsThemAndNoDefaultServiceTime() {
        CompanyProfile profile = CompanyProfile.create(NAME, Language.ENGLISH, NINE_TO_FIVE);

        assertThat(ProfileDescription.of(profile))
                .isEqualTo(new ProfileDescription(NAME, Language.ENGLISH, NINE_TO_FIVE, Optional.empty()));
        assertThat(profile.savedVersion()).isEmpty();
        assertThat(profile.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAProfile_WhenRenamed_ThenOnlyTheNameChanges() {
        CompanyProfile profile = aProfile();

        profile.rename(new CompanyName("Fast Deliveries"));

        assertThat(ProfileDescription.of(profile))
                .isEqualTo(new ProfileDescription(
                        new CompanyName("Fast Deliveries"), Language.HEBREW, NINE_TO_FIVE, Optional.empty()));
        assertThat(profile.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAProfile_WhenItsLanguageIsChanged_ThenANewUserStartsWithTheNewOne() {
        CompanyProfile profile = aProfile();

        profile.changeLanguage(Language.ENGLISH);

        assertThat(profile.language()).isEqualTo(Language.ENGLISH);
    }

    @Test
    void GivenAProfile_WhenTheDefaultWindowIsChanged_ThenANewCustomerStartsWithTheNewOne() {
        CompanyProfile profile = aProfile();

        profile.changeDefaultWindow(EIGHT_TO_NOON);

        assertThat(profile.defaultWindow()).isEqualTo(EIGHT_TO_NOON);
    }

    @Test
    void GivenAProfileWithNoDefaultServiceTime_WhenOneIsSet_ThenANewCustomerStartsWithIt() {
        CompanyProfile profile = aProfile();

        profile.changeDefaultServiceTime(new DefaultServiceTime(300));

        assertThat(profile.defaultServiceTime()).contains(new DefaultServiceTime(300));
        assertThat(profile.name()).isEqualTo(NAME);
    }
}
