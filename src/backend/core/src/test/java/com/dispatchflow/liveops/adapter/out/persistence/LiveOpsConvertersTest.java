package com.dispatchflow.liveops.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.alert.DedupeKey;
import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.liveops.domain.proof.ProofNote;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.metamodel.spi.ValueAccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Live Operations' converters and its instantiator: each value's column, its null, and the stored
 * values its type refuses.
 */
class LiveOpsConvertersTest {

    private static final UUID KEY = UUID.fromString("0190a5e2-6c69-7600-8000-000000000001");
    private static final Instant SENT = Instant.parse("2031-03-01T08:00:00Z");
    private static final Instant RECEIVED = Instant.parse("2031-03-01T08:05:00Z");
    private static final Instant PAST_YEAR_9999 = Instant.parse("+10000-01-01T00:00:00Z");

    /** Each converter with a value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new RevisionNoConverter(), RevisionNo.of(3), 3),
                Arguments.of(new HeldReportIdConverter(), HeldReportId.of(KEY), KEY),
                Arguments.of(new ProofIdConverter(), ProofId.of(KEY), KEY),
                Arguments.of(new ProofNoteConverter(), new ProofNote("by the blue door"), "by the blue door"),
                Arguments.of(new DedupeKeyConverter(), new DedupeKey("STOP_FAILED:" + KEY), "STOP_FAILED:" + KEY));
    }

    /** Each converter of a type that refuses some stored values, with one it refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new RevisionNoConverter(), 0, "RevisionNo"),
                Arguments.of(new ProofNoteConverter(), " ", "ProofNote"));
    }

    /** Stored values in the alphabetical order of the attributes' names, as Hibernate hands them. */
    private static ValueAccess stored(@Nullable Object... values) {
        return () -> values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenALiveOpsValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenALiveOpsConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }

    @Test
    void GivenAStoredCorrectedTime_WhenRebuilt_ThenItIsTheTimeStored() {
        assertThat(new CorrectedTimeInstantiator().instantiate(stored(RECEIVED, SENT, RECEIVED)))
                .isEqualTo(CorrectedTime.of(SENT, RECEIVED, RECEIVED));
    }

    @Test
    void GivenNoStoredCorrectedTime_WhenRebuilt_ThenThereIsNone() {
        assertThat(new CorrectedTimeInstantiator().instantiate(stored(null, null, null)))
                .isNull();
    }

    @Test
    void GivenAStoredDeviceTimePastTheYear9999_WhenRebuilt_ThenStoredStateInvalidNamesTheTime() {
        assertThatThrownBy(
                        () -> new CorrectedTimeInstantiator().instantiate(stored(RECEIVED, PAST_YEAR_9999, RECEIVED)))
                .isInstanceOfSatisfying(StoredStateInvalid.class, invalid -> {
                    assertThat(invalid.subject()).isEqualTo("CorrectedTime");
                    assertThat(invalid.detail()).isEqualTo("a stored value is refused with DEVICE_TIME_OUT_OF_RANGE");
                });
    }

    @Test
    void GivenAStoredCorrectedTimeMissingItsReceipt_WhenRebuilt_ThenStoredStateInvalid() {
        assertThatThrownBy(() -> new CorrectedTimeInstantiator().instantiate(stored(RECEIVED, SENT, null)))
                .isInstanceOf(StoredStateInvalid.class);
    }
}
