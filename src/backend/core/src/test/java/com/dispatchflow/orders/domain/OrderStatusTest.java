package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.OrderBuilder.anOrder;
import static com.dispatchflow.orders.domain.OrdersTestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.orders.domain.Placement.PlanStanding;
import com.dispatchflow.shared.Attempt;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link OrderStatus#derive}: the first status that fits, facts before a cancellation (BR-33, DL §9.6),
 * and a cancelled or abandoned plan's orders unplanned (FR-337, BR-96, BR-131).
 */
class OrderStatusTest {

    private static final Attempt FIRST = Attempt.FIRST;
    private static final Attempt SECOND = Attempt.of(2);

    static Stream<Arguments> cases() {
        Order active = anOrder().build();
        Order cancelled = anOrder().cancelledAt(NOW).build();
        Order putBack = anOrder().atAttempt(2).build();
        Placement planned = new Placement.Planned(FIRST, PlanStanding.IN_FORCE);
        Placement dropped = new Placement.Dropped(FIRST, PlanStanding.IN_FORCE);
        StopState noFact = new StopState.NoFact();
        return Stream.of(
                Arguments.of("on no plan", active, new Placement.NotPlaced(), noFact, OrderStatus.UNPLANNED),
                Arguments.of("on a route", active, planned, noFact, OrderStatus.PLANNED),
                Arguments.of(
                        "arrived is still planned", active, planned, new StopState.Arrived(FIRST), OrderStatus.PLANNED),
                Arguments.of("dropped", active, dropped, noFact, OrderStatus.DROPPED),
                Arguments.of("delivered", active, planned, new StopState.Delivered(FIRST), OrderStatus.DELIVERED),
                Arguments.of("failed", active, planned, new StopState.Failed(FIRST), OrderStatus.FAILED),
                Arguments.of("cancelled", cancelled, planned, noFact, OrderStatus.CANCELLED),
                Arguments.of(
                        "delivered before a cancellation",
                        cancelled,
                        planned,
                        new StopState.Delivered(FIRST),
                        OrderStatus.DELIVERED),
                Arguments.of(
                        "on an abandoned plan",
                        active,
                        new Placement.Planned(FIRST, PlanStanding.ABANDONED),
                        noFact,
                        OrderStatus.UNPLANNED),
                Arguments.of(
                        "on a cancelled plan",
                        active,
                        new Placement.Planned(FIRST, PlanStanding.CANCELLED),
                        noFact,
                        OrderStatus.UNPLANNED),
                Arguments.of(
                        "dropped by a cancelled plan",
                        active,
                        new Placement.Dropped(FIRST, PlanStanding.CANCELLED),
                        noFact,
                        OrderStatus.UNPLANNED),
                Arguments.of(
                        "put back, its old stop failed",
                        putBack,
                        planned,
                        new StopState.Failed(FIRST),
                        OrderStatus.UNPLANNED),
                Arguments.of(
                        "put back and planned again",
                        putBack,
                        new Placement.Planned(SECOND, PlanStanding.IN_FORCE),
                        new StopState.Failed(FIRST),
                        OrderStatus.PLANNED));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void GivenTheThreeOwnersAnswers_WhenTheStatusIsDerived_ThenTheFirstThatFitsIsTaken(
            String scenario, Order order, Placement placement, StopState progress, OrderStatus expected) {
        assertThat(OrderStatus.derive(order, placement, progress)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void GivenAReadModelsValues_WhenTheStatusIsDerived_ThenItMatchesTheAggregatesStatus(
            String scenario, Order order, Placement placement, StopState progress, OrderStatus expected) {
        assertThat(OrderStatus.derive(order.attempt(), order.isCancelled(), placement, progress))
                .isEqualTo(expected);
    }
}
