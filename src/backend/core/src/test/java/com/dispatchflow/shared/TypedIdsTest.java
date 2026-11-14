package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TypedIdsTest {

    private static final UUID UUID_V7 = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final UUID UUID_V4 = UUID.fromString("3f2b8c1e-9d4a-4b6f-8e2d-1a5c7b9e0f3d");

    static Stream<Function<UUID, Record>> ids() {
        return Stream.of(
                AccountId::of,
                SessionId::of,
                DepotId::of,
                VehicleId::of,
                CustomerId::of,
                OrderId::of,
                ImportBatchId::of,
                PlanId::of,
                RouteId::of,
                StopId::of,
                JobId::of,
                ProposalId::of,
                AlertId::of,
                RatingRequestId::of);
    }

    @Test
    void GivenTheKernel_WhenItsIdsAreCounted_ThenThereAreFourteen() {
        assertThat(ids().map(id -> id.apply(UUID_V7).getClass()).distinct()).hasSize(14);
    }

    @ParameterizedTest
    @MethodSource("ids")
    void GivenAUuid_WhenWrapped_ThenTheIdHoldsItAndEqualsAnotherWrappingOfIt(Function<UUID, Record> id) {
        assertThat(id.apply(UUID_V7))
                .hasFieldOrPropertyWithValue("value", UUID_V7)
                .isEqualTo(id.apply(UUID_V7))
                .isNotEqualTo(id.apply(UUID_V4));
    }

    @ParameterizedTest
    @MethodSource("ids")
    void GivenAUuidOfAnotherVersion_WhenWrapped_ThenItIsAccepted(Function<UUID, Record> id) {
        assertThat(id.apply(UUID_V4)).hasFieldOrPropertyWithValue("value", UUID_V4);
    }

    @Test
    void GivenTwoKindsOfIdOverOneUuid_WhenCompared_ThenTheyDiffer() {
        assertThat(List.<Record>of(PlanId.of(UUID_V7))).doesNotContain(RouteId.of(UUID_V7));
    }
}
