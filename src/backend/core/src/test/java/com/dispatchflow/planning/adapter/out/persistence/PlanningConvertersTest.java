package com.dispatchflow.planning.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.planning.domain.SolveLimit;
import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.planning.domain.plan.Seed;
import com.dispatchflow.planning.domain.plan.SolverName;
import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.planning.domain.settings.TravelFactor;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Planning's converters: each value's column, its null, and the stored values its type refuses. */
class PlanningConvertersTest {

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new SolverNameConverter(), SolverName.OR_TOOLS, "ortools"),
                // The limit's nanoseconds, as every duration is stored.
                Arguments.of(new SolveLimitConverter(), SolveLimit.REOPTIMIZATION, 20_000_000_000L),
                Arguments.of(new SeedConverter(), Seed.of(-7), -7L),
                Arguments.of(new RevisionNoConverter(), RevisionNo.of(3), 3),
                Arguments.of(new RuleCodeConverter(), RuleCode.LOCKS, "FR-147"),
                Arguments.of(new BufferConverter(), Buffer.percent(new BigDecimal("12.5")), new BigDecimal("12.5")),
                Arguments.of(
                        new TravelFactorConverter(), TravelFactor.of(new BigDecimal("1.3")), new BigDecimal("1.3")),
                Arguments.of(new DepartureAllowanceConverter(), DepartureAllowance.ofMinutes(15), 15),
                Arguments.of(new RouteIndexConverter(), RouteIndex.of(30), 30));
    }

    /** Each converter with a stored value its type refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new SolverNameConverter(), "OR Tools", "SolverName"),
                Arguments.of(new SolveLimitConverter(), Duration.ofSeconds(61).toNanos(), "SolveLimit"),
                Arguments.of(
                        new SolveLimitConverter(), Duration.ofMillis(20_500).toNanos(), "SolveLimit"),
                Arguments.of(new RevisionNoConverter(), 0, "RevisionNo"),
                Arguments.of(new RuleCodeConverter(), "BR05", "RuleCode"),
                Arguments.of(new BufferConverter(), new BigDecimal("-0.1"), "Buffer"),
                Arguments.of(new TravelFactorConverter(), BigDecimal.ZERO, "TravelFactor"),
                Arguments.of(new DepartureAllowanceConverter(), -1, "DepartureAllowance"),
                Arguments.of(new RouteIndexConverter(), 0, "RouteIndex"),
                Arguments.of(new RouteIndexConverter(), 31, "RouteIndex"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAPlanningConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }
}
