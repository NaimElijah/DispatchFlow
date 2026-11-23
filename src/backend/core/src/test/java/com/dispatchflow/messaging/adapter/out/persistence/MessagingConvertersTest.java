package com.dispatchflow.messaging.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.messaging.domain.QuarterHour;
import com.dispatchflow.messaging.domain.message.ProviderReference;
import com.dispatchflow.messaging.domain.rating.Comment;
import com.dispatchflow.messaging.domain.rating.Rating;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Messaging's converters: each value's column, its null, and the stored values its type refuses. */
class MessagingConvertersTest {

    private static final Instant QUARTER = Instant.parse("2030-01-01T08:15:00Z");

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(
                        new MessageTextConverter(),
                        MessageText.ofStored("Shefa Deliveries: on its way"),
                        "Shefa Deliveries: on its way"),
                Arguments.of(new QuarterHourConverter(), new QuarterHour(QUARTER), QUARTER),
                Arguments.of(new ProviderReferenceConverter(), new ProviderReference("provider-1"), "provider-1"),
                Arguments.of(new CommentConverter(), Comment.ofStored("left at the door"), "left at the door"),
                Arguments.of(new RatingConverter(), Rating.of(7), 7),
                Arguments.of(
                        new DispatchPhoneConverter(),
                        DispatchPhone.of(PhoneNumber.ofStored("+97235551234")),
                        "+97235551234"),
                Arguments.of(new PhoneHashConverter(), new PhoneHash("hash-1"), "hash-1"));
    }

    /** Each converter of a type that refuses some stored values, with one it refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new MessageTextConverter(), "", "MessageText"),
                Arguments.of(new QuarterHourConverter(), QUARTER.plusSeconds(60), "QuarterHour"),
                Arguments.of(new CommentConverter(), " ", "Comment"),
                Arguments.of(new RatingConverter(), 11, "Rating"),
                Arguments.of(new DispatchPhoneConverter(), "+442079460000", "DispatchPhone"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAMessagingValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAMessagingConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }
}
