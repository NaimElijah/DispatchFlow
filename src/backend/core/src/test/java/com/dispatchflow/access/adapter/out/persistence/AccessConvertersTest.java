package com.dispatchflow.access.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.PasswordHash;
import com.dispatchflow.access.domain.companyprofile.CompanyName;
import com.dispatchflow.access.domain.companyprofile.DefaultServiceTime;
import com.dispatchflow.access.domain.session.SessionGeneration;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Access's converters: each value's column, its null, and the stored values its type refuses. */
class AccessConvertersTest {

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new EmailAddressConverter(), EmailAddress.parse("dana@example.com"), "dana@example.com"),
                Arguments.of(new TokenHashConverter(), new TokenHash("token-hash"), "token-hash"),
                Arguments.of(new PasswordHashConverter(), new PasswordHash("password-hash"), "password-hash"),
                Arguments.of(new AccountGenerationConverter(), AccountGeneration.of(4), 4L),
                Arguments.of(new SessionGenerationConverter(), SessionGeneration.of(2), 2L),
                Arguments.of(new CompanyNameConverter(), new CompanyName("Shefa Deliveries"), "Shefa Deliveries"),
                Arguments.of(new DefaultServiceTimeConverter(), new DefaultServiceTime(240), 240L));
    }

    /** Each converter of a type that refuses some stored values, with one it refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new EmailAddressConverter(), "not-an-address", "EmailAddress"),
                Arguments.of(new AccountGenerationConverter(), -1L, "AccountGeneration"),
                Arguments.of(new SessionGenerationConverter(), -1L, "SessionGeneration"),
                Arguments.of(new CompanyNameConverter(), " ", "CompanyName"),
                Arguments.of(new DefaultServiceTimeConverter(), 0L, "DefaultServiceTime"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAnAccessValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
            AttributeConverter converter, Object value, Object column) {
        assertThat(converter.convertToDatabaseColumn(value)).isEqualTo(column);
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenANullAttributeOrColumn_WhenConverted_ThenItStaysNull(AttributeConverter converter) {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("refusedColumns")
    void GivenAStoredValueTheTypeRefuses_WhenReadBack_ThenStoredStateInvalidNamesTheType(
            AttributeConverter converter, Object column, String type) {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(column))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo(type));
    }

    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAnAccessConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }
}
