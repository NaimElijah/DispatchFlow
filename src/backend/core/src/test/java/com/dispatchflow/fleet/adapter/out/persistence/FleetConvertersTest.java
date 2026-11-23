package com.dispatchflow.fleet.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.fleet.domain.driver.DriverName;
import com.dispatchflow.fleet.domain.register.DepotName;
import com.dispatchflow.fleet.domain.register.Plate;
import com.dispatchflow.fleet.domain.register.VehicleName;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Fleet's converters: each value's column, its null, and the stored values its type refuses. */
class FleetConvertersTest {

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new DriverNameConverter(), DriverName.of("Dana Levi"), "Dana Levi"),
                Arguments.of(new DepotNameConverter(), DepotName.of("North"), "North"),
                Arguments.of(new VehicleNameConverter(), VehicleName.of("Van 7"), "Van 7"),
                Arguments.of(new PlateConverter(), Plate.of("12-345-67"), "1234567"));
    }

    /** Each converter with a stored value its type refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new DriverNameConverter(), " ", "DriverName"),
                Arguments.of(new DepotNameConverter(), "", "DepotName"),
                Arguments.of(new VehicleNameConverter(), "‏", "VehicleName"),
                Arguments.of(new PlateConverter(), "ABCDEFGHIJK", "Plate"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAFleetValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAFleetConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }
}
