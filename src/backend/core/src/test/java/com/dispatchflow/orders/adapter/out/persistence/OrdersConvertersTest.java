package com.dispatchflow.orders.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.orders.domain.Apartment;
import com.dispatchflow.orders.domain.ContactName;
import com.dispatchflow.orders.domain.DailyWindow;
import com.dispatchflow.orders.domain.DriverNote;
import com.dispatchflow.orders.domain.EntranceCode;
import com.dispatchflow.orders.domain.ExternalReference;
import com.dispatchflow.orders.domain.FileHash;
import com.dispatchflow.orders.domain.Floor;
import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;
import java.util.stream.Stream;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Orders' single-column converters and its one instantiator: each value's column, its null, and
 * the stored values its type refuses. {@link RowErrorsConverterTest} covers the JSON column.
 */
class OrdersConvertersTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime FIVE_PM = LocalTime.of(17, 0);

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new AddressConverter(), Address.of("הרצל 5, תל אביב"), "הרצל 5, תל אביב"),
                Arguments.of(new ContactNameConverter(), ContactName.of("דנה לוי"), "דנה לוי"),
                Arguments.of(
                        new DriverNoteConverter(), DriverNote.of("שער אחורי").orElseThrow(), "שער אחורי"),
                Arguments.of(
                        new ExternalReferenceConverter(),
                        ExternalReference.of("SHOP-1001").orElseThrow(),
                        "SHOP-1001"),
                Arguments.of(new FloorConverter(), Floor.of("3").orElseThrow(), "3"),
                Arguments.of(new ApartmentConverter(), Apartment.of("12").orElseThrow(), "12"),
                Arguments.of(
                        new EntranceCodeConverter(), EntranceCode.of("1234#").orElseThrow(), "1234#"),
                Arguments.of(new ServiceTimeConverter(), ServiceTime.ofSeconds(300), 300L),
                Arguments.of(new FileHashConverter(), new FileHash("9f86d081"), "9f86d081"));
    }

    /** Each converter with a stored value its type refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new AddressConverter(), "-", "Address"),
                Arguments.of(new ContactNameConverter(), " ", "ContactName"),
                Arguments.of(new DriverNoteConverter(), " ", "DriverNote"),
                Arguments.of(new ExternalReferenceConverter(), "", "ExternalReference"),
                Arguments.of(new FloorConverter(), "x".repeat(101), "Floor"),
                Arguments.of(new ApartmentConverter(), "", "Apartment"),
                Arguments.of(new EntranceCodeConverter(), "\u0007", "EntranceCode"),
                Arguments.of(new ServiceTimeConverter(), 0L, "ServiceTime"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAnOrdersValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAnOrdersConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }

    /** Stored values in the alphabetical order of the attributes' names, as Hibernate hands them. */
    private static ValueAccess stored(@Nullable Object... values) {
        return () -> values;
    }

    @Test
    void GivenAStoredDailyWindow_WhenRebuilt_ThenItIsTheWindowStored() {
        assertThat(new DailyWindowInstantiator().instantiate(stored(FIVE_PM, NINE)))
                .isEqualTo(DailyWindow.of(NINE, FIVE_PM));
    }

    @Test
    void GivenAStoredDailyWindowThatEndsBeforeItStarts_WhenRebuilt_ThenStoredStateInvalidNamesIt() {
        assertThatThrownBy(() -> new DailyWindowInstantiator().instantiate(stored(NINE, FIVE_PM)))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("DailyWindow"));
    }

    @Test
    void GivenAStoredDailyWindowMissingItsEnd_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new DailyWindowInstantiator().instantiate(stored(null, NINE)))
                .isInstanceOf(StoredStateInvalid.class);
    }
}
