package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.dispatchflow.shared.adapter.out.persistence.id.AccountIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.AlertIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.CustomerIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.DepotIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.ImportBatchIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.JobIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.OrderIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.PlanIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.ProposalIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.RatingRequestIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.RouteIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.SessionIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.StopIdConverter;
import com.dispatchflow.shared.adapter.out.persistence.id.VehicleIdConverter;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KernelConvertersTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final int TYPED_IDS = 14;

    /** Each converter with a kernel value and the column value it stores. */
    static Stream<Arguments> conversions() {
        return Stream.of(
                Arguments.of(new AttemptConverter(), Attempt.of(2), 2),
                Arguments.of(new PriorityConverter(), Priority.of(Priority.HIGHEST), Priority.HIGHEST),
                Arguments.of(
                        new DeliveryDateConverter(),
                        DeliveryDate.of(LocalDate.of(2027, 3, 26)),
                        LocalDate.of(2027, 3, 26)),
                Arguments.of(
                        new IsraelDateTimeConverter(),
                        IsraelDateTime.of(LocalDateTime.of(2027, 7, 13, 8, 30)),
                        LocalDateTime.of(2027, 7, 13, 8, 30)),
                Arguments.of(new WeightConverter(), Weight.kilograms(new BigDecimal("12.5")), new BigDecimal("12.5")),
                Arguments.of(new VolumeConverter(), Volume.litres(new BigDecimal("40")), new BigDecimal("40")),
                Arguments.of(new PhoneNumberConverter(), PhoneNumber.ofStored("+972523456789"), "+972523456789"),
                Arguments.of(
                        new EventIdConverter(),
                        new EventId("PlanOpened", ID, Version.of(0), 0),
                        "PlanOpened:0190a5e2-7c3b-7def-8a12-3456789abcde:0:0"),
                Arguments.of(new AccountIdConverter(), AccountId.of(ID), ID),
                Arguments.of(new AlertIdConverter(), AlertId.of(ID), ID),
                Arguments.of(new CustomerIdConverter(), CustomerId.of(ID), ID),
                Arguments.of(new DepotIdConverter(), DepotId.of(ID), ID),
                Arguments.of(new ImportBatchIdConverter(), ImportBatchId.of(ID), ID),
                Arguments.of(new JobIdConverter(), JobId.of(ID), ID),
                Arguments.of(new OrderIdConverter(), OrderId.of(ID), ID),
                Arguments.of(new PlanIdConverter(), PlanId.of(ID), ID),
                Arguments.of(new ProposalIdConverter(), ProposalId.of(ID), ID),
                Arguments.of(new RatingRequestIdConverter(), RatingRequestId.of(ID), ID),
                Arguments.of(new RouteIdConverter(), RouteId.of(ID), ID),
                Arguments.of(new SessionIdConverter(), SessionId.of(ID), ID),
                Arguments.of(new StopIdConverter(), StopId.of(ID), ID),
                Arguments.of(new VehicleIdConverter(), VehicleId.of(ID), ID));
    }

    /** Each converter of a type that refuses some stored values, with one it refuses. */
    static Stream<Arguments> refusedColumns() {
        return Stream.of(
                Arguments.of(new AttemptConverter(), 0, "Attempt"),
                Arguments.of(new PriorityConverter(), Priority.LOWEST + 1, "Priority"),
                Arguments.of(new DeliveryDateConverter(), LocalDate.of(10_000, 1, 1), "DeliveryDate"),
                // Israel skips from 02:00 to 03:00 on 26 March 2027.
                Arguments.of(new IsraelDateTimeConverter(), LocalDateTime.of(2027, 3, 26, 2, 30), "IsraelDateTime"),
                Arguments.of(new WeightConverter(), BigDecimal.ZERO, "Weight"),
                Arguments.of(new VolumeConverter(), new BigDecimal("-1"), "Volume"),
                Arguments.of(new PhoneNumberConverter(), "0523456789", "PhoneNumber"),
                Arguments.of(new EventIdConverter(), "PlanOpened:not-a-uuid:0:0", "EventId"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Each row pairs a converter with its own types.
    @ParameterizedTest
    @MethodSource("conversions")
    void GivenAKernelValue_WhenStoredAndReadBack_ThenItIsTheSameValue(
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
    void GivenAKernelConverter_WhenItsDeclarationIsRead_ThenItIsAutoApplied(Object converter) {
        Converter declared = converter.getClass().getAnnotation(Converter.class);

        assertThat(declared).isNotNull();
        assertThat(declared.autoApply()).isTrue();
    }

    @Test
    void GivenTheConverters_WhenTheTypedIdsAreCounted_ThenEachOfTheFourteenHasOne() {
        assertThat(conversions().filter(row -> row.get()[2] instanceof UUID)).hasSize(TYPED_IDS);
    }
}
