package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void GivenANewAggregate_WhenItsVersionIsRead_ThenItIsZero() {
        assertThat(Version.NEW.value()).isZero();
    }

    @Test
    void GivenAVersion_WhenTheNextIsTaken_ThenItIsOneMore() {
        assertThat(Version.of(4).next()).isEqualTo(Version.of(5));
        assertThat(Version.NEW.next()).isEqualTo(Version.of(1));
    }

    @Test
    void GivenANegativeStoredVersion_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> Version.of(-1)).isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
            assertThat(invalid.subject()).isEqualTo("Version");
            assertThat(invalid).hasMessage("Version: a version is never below zero");
        });
    }
}
