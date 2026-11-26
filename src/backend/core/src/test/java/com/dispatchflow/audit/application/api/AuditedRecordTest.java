package com.dispatchflow.audit.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What an audited operation names (NFR-93, ADR-110): each {@code of} factory keeps the kind matched
 * to the kernel ID it is given.
 */
class AuditedRecordTest {

    private static final UUID ID = UUID.fromString("0193a4b2-7c1d-7e00-8000-0000000000e1");

    static Stream<Arguments> eachKernelId() {
        return Stream.of(
                Arguments.of(AuditedRecord.of(AccountId.of(ID)), AuditedKind.ACCOUNT),
                Arguments.of(AuditedRecord.of(SessionId.of(ID)), AuditedKind.SESSION),
                Arguments.of(AuditedRecord.of(DepotId.of(ID)), AuditedKind.DEPOT),
                Arguments.of(AuditedRecord.of(VehicleId.of(ID)), AuditedKind.VEHICLE),
                Arguments.of(AuditedRecord.of(CustomerId.of(ID)), AuditedKind.CUSTOMER),
                Arguments.of(AuditedRecord.of(OrderId.of(ID)), AuditedKind.ORDER),
                Arguments.of(AuditedRecord.of(ImportBatchId.of(ID)), AuditedKind.IMPORT_BATCH),
                Arguments.of(AuditedRecord.of(PlanId.of(ID)), AuditedKind.PLAN),
                Arguments.of(AuditedRecord.of(RouteId.of(ID)), AuditedKind.ROUTE),
                Arguments.of(AuditedRecord.of(StopId.of(ID)), AuditedKind.STOP),
                Arguments.of(AuditedRecord.of(JobId.of(ID)), AuditedKind.JOB),
                Arguments.of(AuditedRecord.of(ProposalId.of(ID)), AuditedKind.PROPOSAL),
                Arguments.of(AuditedRecord.of(AlertId.of(ID)), AuditedKind.ALERT),
                Arguments.of(AuditedRecord.of(RatingRequestId.of(ID)), AuditedKind.RATING_REQUEST));
    }

    @ParameterizedTest
    @MethodSource("eachKernelId")
    void GivenEachKernelId_WhenNamed_ThenItsKindMatches(AuditedRecord named, AuditedKind kind) {
        assertThat(named).isEqualTo(new AuditedRecord(kind, ID));
    }

    @Test
    void GivenTheFactories_WhenCounted_ThenEveryKindHasOne() {
        assertThat(eachKernelId().map(arguments -> arguments.get()[1]))
                .containsExactlyInAnyOrder((Object[]) AuditedKind.values());
        assertThat(Arrays.stream(AuditedRecord.class.getMethods())
                        .filter(method -> method.getName().equals("of")))
                .hasSize(AuditedKind.values().length);
    }

    @Test
    void GivenNoKind_WhenBuilt_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> new AuditedRecord(nothing(), ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind");
    }

    @Test
    void GivenNoId_WhenBuilt_ThenItFailsLoudlyNamingIt() {
        assertThatThrownBy(() -> new AuditedRecord(AuditedKind.PLAN, nothing()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    // A null that the null-marked signature doesn't admit, to test the refusal itself.
    private static <T> T nothing() {
        return null;
    }
}
