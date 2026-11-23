package com.dispatchflow.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Audit's converters: each value's column, its null, and the stored values its type refuses. */
class AuditConvertersTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new OperationNameConverter(), OperationName.of("publishPlan"), "publishPlan"),
                Arguments.of(new TraceIdConverter(), TraceId.of(TRACE), TRACE),
                Arguments.of(new ErrorCodeConverter(), ErrorCode.of("STALE_VERSION"), "STALE_VERSION"),
                Arguments.of(new AppVersionConverter(), new AppVersion(1, 4, 2), "1.4.2"));
    }

    /** Each converter with a stored value its type refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new OperationNameConverter(), "1publishPlan", "OperationName"),
                Arguments.of(new TraceIdConverter(), "0".repeat(32), "TraceId"),
                Arguments.of(new ErrorCodeConverter(), "stale_version", "ErrorCode"),
                Arguments.of(new AppVersionConverter(), "1.4", "AppVersion"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAnAuditValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAnAuditConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }
}
