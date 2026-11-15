package com.dispatchflow.access.domain;

import static com.dispatchflow.access.domain.AccountBuilder.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StoredStateInvalid;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpaqueValuesTest {

    @Test
    void GivenEveryValueHoldingAnAddressOrACredential_WhenChecked_ThenItIsMarkedPersonalDataAndPrintsRedacted() {
        EmailAddress email = EmailAddress.parse("dana@example.com");
        List<Object> values = List.of(
                email,
                NewPassword.of("tz7!kw0q", CommonPasswords.of(List.of())),
                new PasswordHash("stored-hash"),
                new TokenHash("token-hash"),
                new LinkPurpose.EmailChange(email, ACCOUNT),
                EmailHolder.nobody(email),
                EmailHolder.account(email, AccountId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-000000000001"))));

        for (Object value : values) {
            assertThat(value).isInstanceOf(PersonalData.class);
            assertThat(value.toString()).contains("redacted").doesNotContain("dana", "hash", "tz7");
        }
    }

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
