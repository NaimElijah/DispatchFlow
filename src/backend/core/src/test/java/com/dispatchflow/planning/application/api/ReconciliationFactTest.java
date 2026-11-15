package com.dispatchflow.planning.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.planning.application.api.ReconciliationFact.Kind;
import com.dispatchflow.shared.RouteId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReconciliationFactTest {

    private static final RouteId FROM = RouteId.of(UUID.fromString("0190a5e2-7c3b-7def-8a12-0000000000c1"));

    @Test
    void GivenAMovedStopWithTheRouteItLeft_WhenTheFactIsBuilt_ThenItKeepsTheRoute() {
        ReconciliationFact fact = new ReconciliationFact(Kind.MOVED_FROM_ROUTE, Optional.of(FROM));

        assertThat(fact.from()).contains(FROM);
    }

    @Test
    void GivenAMovedStopWithNoRoute_WhenTheFactIsBuilt_ThenItIsRefused() {
        assertThatThrownBy(() -> new ReconciliationFact(Kind.MOVED_FROM_ROUTE, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = "MOVED_FROM_ROUTE", mode = EnumSource.Mode.EXCLUDE)
    void GivenAnotherKindWithNoRoute_WhenTheFactIsBuilt_ThenItIsKept(Kind kind) {
        assertThat(new ReconciliationFact(kind, Optional.empty()).from()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = "MOVED_FROM_ROUTE", mode = EnumSource.Mode.EXCLUDE)
    void GivenAnotherKindWithARoute_WhenTheFactIsBuilt_ThenItIsRefused(Kind kind) {
        assertThatThrownBy(() -> new ReconciliationFact(kind, Optional.of(FROM)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
