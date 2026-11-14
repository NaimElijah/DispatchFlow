package com.dispatchflow.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** ADR-110: a record an operation names is a kernel ID with its kind (NFR-93). */
class NamedRecordTest {

    private static final UUID ID = UUID.fromString("0193a4b2-7c1d-7e00-8000-000000000001");

    static Stream<Arguments> everyKernelId() {
        return Stream.of(
                Arguments.of(factory(id -> NamedRecord.of(AccountId.of(id))), RecordKind.ACCOUNT),
                Arguments.of(factory(id -> NamedRecord.of(new SessionId(id))), RecordKind.SESSION),
                Arguments.of(factory(id -> NamedRecord.of(new DepotId(id))), RecordKind.DEPOT),
                Arguments.of(factory(id -> NamedRecord.of(new VehicleId(id))), RecordKind.VEHICLE),
                Arguments.of(factory(id -> NamedRecord.of(new CustomerId(id))), RecordKind.CUSTOMER),
                Arguments.of(factory(id -> NamedRecord.of(new OrderId(id))), RecordKind.ORDER),
                Arguments.of(factory(id -> NamedRecord.of(new ImportBatchId(id))), RecordKind.IMPORT_BATCH),
                Arguments.of(factory(id -> NamedRecord.of(new PlanId(id))), RecordKind.PLAN),
                Arguments.of(factory(id -> NamedRecord.of(new RouteId(id))), RecordKind.ROUTE),
                Arguments.of(factory(id -> NamedRecord.of(new StopId(id))), RecordKind.STOP),
                Arguments.of(factory(id -> NamedRecord.of(new JobId(id))), RecordKind.JOB),
                Arguments.of(factory(id -> NamedRecord.of(new ProposalId(id))), RecordKind.PROPOSAL),
                Arguments.of(factory(id -> NamedRecord.of(new AlertId(id))), RecordKind.ALERT),
                Arguments.of(factory(id -> NamedRecord.of(new RatingRequestId(id))), RecordKind.RATING_REQUEST));
    }

    // Gives each lambda its target type, which Arguments.of(Object...) cannot.
    private static Function<UUID, NamedRecord> factory(Function<UUID, NamedRecord> factory) {
        return factory;
    }

    @ParameterizedTest
    @MethodSource("everyKernelId")
    void GivenAKernelId_WhenNamed_ThenTheRecordHasItsKindAndItsUuid(
            Function<UUID, NamedRecord> factory, RecordKind expectedKind) {
        NamedRecord named = factory.apply(ID);

        assertThat(named.kind()).isEqualTo(expectedKind);
        assertThat(named.id()).isEqualTo(UUID.fromString("0193a4b2-7c1d-7e00-8000-000000000001"));
    }
}
