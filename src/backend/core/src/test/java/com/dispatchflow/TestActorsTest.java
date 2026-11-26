package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.ActorRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestActorsTest {

    @Test
    void GivenTestActorsDriver_WhenItIsBuilt_ThenItsCallContextNamesItsAccountAndASession() {
        Actor driver = TestActors.driver();

        assertThat(driver.roles()).containsExactly(ActorRole.DRIVER);
        assertThat(driver.call().user()).contains(driver.account());
        assertThat(driver.call().session()).contains(TestActors.SESSION);
        assertThat(driver.call().client()).isEmpty();
    }

    @Test
    void GivenTheThreeSeededRoles_WhenTheirActorsAreBuilt_ThenEachHoldsItsOwnRoleAndAccount() {
        assertThat(TestActors.dispatcher().roles()).containsExactly(ActorRole.DISPATCHER);
        assertThat(TestActors.admin().roles()).containsExactly(ActorRole.ADMIN);
        assertThat(List.of(
                        TestActors.driver().account(),
                        TestActors.dispatcher().account(),
                        TestActors.admin().account()))
                .doesNotHaveDuplicates();
    }

    @Test
    void GivenAnEntryTheListLacks_WhenItsActorIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> TestActors.of("nobody"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nobody");
    }
}
