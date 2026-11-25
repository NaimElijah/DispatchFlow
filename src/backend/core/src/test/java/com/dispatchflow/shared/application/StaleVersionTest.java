package com.dispatchflow.shared.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.Version;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaleVersionTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");

    @Test
    void GivenTheVersionTheCallerRead_WhenItIsTheLoadedOne_ThenTheChangeGoesOn() {
        assertThatCode(() -> StaleVersion.requireCurrent("Plan", ID, Version.of(4), Version.of(4)))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenAnOlderVersion_WhenCheckedAgainstTheLoadedOne_ThenStaleVersionNamesTheKindAndTheId() {
        assertThatThrownBy(() -> StaleVersion.requireCurrent("Plan", ID, Version.of(5), Version.of(4)))
                .isInstanceOfSatisfying(StaleVersion.class, stale -> {
                    assertThat(stale.kind()).isEqualTo("Plan");
                    assertThat(stale.id()).isEqualTo(ID);
                    assertThat(stale.code()).isEqualTo(StaleVersion.CODE);
                })
                .hasMessage("Plan " + ID + " changed since it was read");
    }

    @Test
    void GivenANewerVersionThanTheLoadedOne_WhenChecked_ThenStaleVersion() {
        assertThatThrownBy(() -> StaleVersion.requireCurrent("Plan", ID, Version.of(0), Version.of(1)))
                .isInstanceOf(StaleVersion.class);
    }

    @Test
    void GivenNothingStoredAndACallerWhoReadNothing_WhenChecked_ThenTheFirstSaveGoesOn() {
        assertThatCode(() -> StaleVersion.requireCurrent("CompanyProfile", ID, Optional.empty(), Optional.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenAStoredVersionTheCallerRead_WhenChecked_ThenTheChangeGoesOn() {
        assertThatCode(() -> StaleVersion.requireCurrent(
                        "CompanyProfile", ID, Optional.of(Version.of(3)), Optional.of(Version.of(3))))
                .doesNotThrowAnyException();
    }

    @Test
    void GivenAStoredAggregateAndACallerWhoReadNothing_WhenChecked_ThenStaleVersion() {
        assertThatThrownBy(() ->
                        StaleVersion.requireCurrent("CompanyProfile", ID, Optional.of(Version.of(0)), Optional.empty()))
                .isInstanceOf(StaleVersion.class);
    }

    @Test
    void GivenNothingStoredAndACallerWhoReadAVersion_WhenChecked_ThenStaleVersion() {
        assertThatThrownBy(() ->
                        StaleVersion.requireCurrent("CompanyProfile", ID, Optional.empty(), Optional.of(Version.of(0))))
                .isInstanceOf(StaleVersion.class);
    }

    @Test
    void GivenAnotherStoredVersion_WhenCheckedAsOptionals_ThenStaleVersion() {
        assertThatThrownBy(() -> StaleVersion.requireCurrent(
                        "CompanyProfile", ID, Optional.of(Version.of(4)), Optional.of(Version.of(3))))
                .isInstanceOf(StaleVersion.class);
    }

    @Test
    void GivenAVersionTheCallerSent_WhenRequireCurrentRefusesIt_ThenTheStaleVersionComparedTheRequestsVersion() {
        assertThatThrownBy(() -> StaleVersion.requireCurrent("Plan", ID, Version.of(5), Version.of(4)))
                .isInstanceOfSatisfying(
                        StaleVersion.class,
                        stale -> assertThat(stale.comparedTheRequestsVersion()).isTrue());
    }

    @Test
    void GivenOptionalVersions_WhenRequireCurrentRefusesThem_ThenTheStaleVersionComparedTheRequestsVersion() {
        assertThatThrownBy(() ->
                        StaleVersion.requireCurrent("CompanyProfile", ID, Optional.of(Version.of(0)), Optional.empty()))
                .isInstanceOfSatisfying(
                        StaleVersion.class,
                        stale -> assertThat(stale.comparedTheRequestsVersion()).isTrue());
    }

    @Test
    void GivenASavesConflict_WhenAStaleVersionIsMadeWithItsConstructors_ThenItComparedNoRequestsVersion() {
        assertThat(new StaleVersion("Plan", ID).comparedTheRequestsVersion()).isFalse();
        assertThat(new StaleVersion("Plan", ID, new IllegalStateException("conflict")).comparedTheRequestsVersion())
                .isFalse();
    }
}
