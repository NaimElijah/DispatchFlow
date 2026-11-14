package com.dispatchflow.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.StoredStateInvalid;
import org.junit.jupiter.api.Test;

class OpaqueValuesTest {

    @Test
    void GivenAPasswordHash_WhenPrinted_ThenItIsHidden() {
        assertThat(new PasswordHash("$argon2id$v=19$secret").toString())
                .isEqualTo("PasswordHash[redacted]")
                .doesNotContain("argon");
    }

    @Test
    void GivenATokenHash_WhenPrinted_ThenItIsHidden() {
        assertThat(new TokenHash("9f86d081884c7d65").toString())
                .isEqualTo("TokenHash[redacted]")
                .doesNotContain("9f86");
    }

    @Test
    void GivenAnAccountGeneration_WhenMovedOn_ThenOnlyTheEarlierOneIsOlder() {
        AccountGeneration next = AccountGeneration.INITIAL.next();

        assertThat(next).isEqualTo(AccountGeneration.of(1));
        assertThat(AccountGeneration.INITIAL.isOlderThan(next)).isTrue();
        assertThat(next.isOlderThan(next)).isFalse();
        assertThat(next.isOlderThan(AccountGeneration.INITIAL)).isFalse();
    }

    @Test
    void GivenASessionGeneration_WhenMovedOn_ThenOnlyTheEarlierOneIsOlder() {
        SessionGeneration next = SessionGeneration.INITIAL.next();

        assertThat(next).isEqualTo(SessionGeneration.of(1));
        assertThat(SessionGeneration.INITIAL.isOlderThan(next)).isTrue();
        assertThat(next.isOlderThan(SessionGeneration.INITIAL)).isFalse();
    }

    @Test
    void GivenANegativeStoredGeneration_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> AccountGeneration.of(-1)).isInstanceOf(StoredStateInvalid.class);
        assertThatThrownBy(() -> SessionGeneration.of(-1)).isInstanceOf(StoredStateInvalid.class);
    }
}
