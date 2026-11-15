package com.dispatchflow.access.domain.companyprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.shared.Version;
import java.time.LocalTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompanyProfileTest {

    private static final CompanyName NAME = new CompanyName("מהיר משלוחים");
    private static final DefaultWindow NINE_TO_FIVE = new DefaultWindow(LocalTime.of(9, 0), LocalTime.of(17, 0));
    private static final DefaultWindow EIGHT_TO_NOON = new DefaultWindow(LocalTime.of(8, 0), LocalTime.of(12, 0));
    private static final Version STORED = Version.of(7);

    /** The profile as stored: by default Hebrew, 09:00 to 17:00, no default service time. */
    private static CompanyProfileSnapshot aProfile(Optional<DefaultServiceTime> serviceTime) {
        return new CompanyProfileSnapshot(STORED, NAME, Language.HEBREW, NINE_TO_FIVE, serviceTime);
    }

    @Test
    void GivenTheDeploymentsSettings_WhenTheProfileIsCreated_ThenItHoldsThemAndNoDefaultServiceTime() {
        CompanyProfile profile = CompanyProfile.create(NAME, Language.ENGLISH, NINE_TO_FIVE);

        assertThat(profile.snapshot())
                .isEqualTo(new CompanyProfileSnapshot(
                        Version.NEW, NAME, Language.ENGLISH, NINE_TO_FIVE, Optional.empty()));
        assertThat(profile.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAProfile_WhenRenamed_ThenOnlyTheNameChanges() {
        CompanyProfile profile = CompanyProfile.restore(aProfile(Optional.empty()));

        profile.rename(new CompanyName("Fast Deliveries"));

        assertThat(profile.snapshot())
                .isEqualTo(new CompanyProfileSnapshot(
                        STORED, new CompanyName("Fast Deliveries"), Language.HEBREW, NINE_TO_FIVE, Optional.empty()));
        assertThat(profile.pendingEvents()).isEmpty();
    }

    @Test
    void GivenAProfile_WhenItsLanguageIsChanged_ThenANewUserStartsWithTheNewOne() {
        CompanyProfile profile = CompanyProfile.restore(aProfile(Optional.empty()));

        profile.changeLanguage(Language.ENGLISH);

        assertThat(profile.language()).isEqualTo(Language.ENGLISH);
    }

    @Test
    void GivenAProfile_WhenTheDefaultWindowIsChanged_ThenANewCustomerStartsWithTheNewOne() {
        CompanyProfile profile = CompanyProfile.restore(aProfile(Optional.empty()));

        profile.changeDefaultWindow(EIGHT_TO_NOON);

        assertThat(profile.defaultWindow()).isEqualTo(EIGHT_TO_NOON);
    }

    @Test
    void GivenAProfileWithNoDefaultServiceTime_WhenOneIsSet_ThenANewCustomerStartsWithIt() {
        CompanyProfile profile = CompanyProfile.restore(aProfile(Optional.empty()));

        profile.changeDefaultServiceTime(new DefaultServiceTime(300));

        assertThat(profile.defaultServiceTime()).contains(new DefaultServiceTime(300));
        assertThat(profile.name()).isEqualTo(NAME);
    }

    static Stream<CompanyProfileSnapshot> everyBuilderState() {
        return Stream.of(aProfile(Optional.empty()), aProfile(Optional.of(new DefaultServiceTime(240))));
    }

    @ParameterizedTest
    @MethodSource("everyBuilderState")
    void GivenAStoredProfile_WhenRestoredFromItsOwnSnapshot_ThenTheSnapshotIsTheSame(CompanyProfileSnapshot stored) {
        CompanyProfile profile = CompanyProfile.restore(stored);

        assertThat(CompanyProfile.restore(profile.snapshot()).snapshot()).isEqualTo(stored);
        assertThat(profile.version()).isEqualTo(STORED);
    }
}
