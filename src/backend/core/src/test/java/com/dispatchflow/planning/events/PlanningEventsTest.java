package com.dispatchflow.planning.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanningEventsTest {

    private static final EventId EVENT = new EventId("SolveSucceeded", uuid(1), Version.of(1), 0);
    private static final Instant AT = Instant.parse("2027-06-15T04:00:00Z");
    private static final JobId JOB = JobId.of(uuid(2));
    private static final PlanId PLAN = PlanId.of(uuid(3));
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2027, 6, 15));
    private static final RouteId ROUTE = RouteId.of(uuid(4));
    private static final VehicleId VEHICLE = VehicleId.of(uuid(5));
    private static final AccountId FROM = AccountId.of(uuid(6));
    private static final AccountId TO = AccountId.of(uuid(7));

    @Test
    void GivenAMissingPart_WhenASolveSucceededIsBuilt_ThenItIsRefused() {
        assertThatNullPointerException().isThrownBy(() -> new SolveSucceeded(null, AT, JOB, PLAN, Lane.DAILY));
        assertThatNullPointerException().isThrownBy(() -> new SolveSucceeded(EVENT, null, JOB, PLAN, Lane.DAILY));
        assertThatNullPointerException().isThrownBy(() -> new SolveSucceeded(EVENT, AT, null, PLAN, Lane.DAILY));
        assertThatNullPointerException().isThrownBy(() -> new SolveSucceeded(EVENT, AT, JOB, null, Lane.DAILY));
        assertThatNullPointerException().isThrownBy(() -> new SolveSucceeded(EVENT, AT, JOB, PLAN, null));
    }

    @Test
    void GivenAMissingPart_WhenASolveFailedIsBuilt_ThenItIsRefused() {
        SolveFailureReason reason = SolveFailureReason.OPTIMIZER_UNAVAILABLE;

        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(null, AT, JOB, PLAN, Lane.DAILY, reason));
        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(EVENT, null, JOB, PLAN, Lane.DAILY, reason));
        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(EVENT, AT, null, PLAN, Lane.DAILY, reason));
        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(EVENT, AT, JOB, null, Lane.DAILY, reason));
        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(EVENT, AT, JOB, PLAN, null, reason));
        assertThatNullPointerException().isThrownBy(() -> new SolveFailed(EVENT, AT, JOB, PLAN, Lane.DAILY, null));
    }

    @Test
    void GivenAMissingPart_WhenARouteHandedOverIsBuilt_ThenItIsRefused() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(null, AT, PLAN, DATE, ROUTE, VEHICLE, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, null, PLAN, DATE, ROUTE, VEHICLE, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, null, DATE, ROUTE, VEHICLE, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, PLAN, null, ROUTE, VEHICLE, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, PLAN, DATE, null, VEHICLE, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, PLAN, DATE, ROUTE, null, FROM, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, PLAN, DATE, ROUTE, VEHICLE, null, TO));
        assertThatNullPointerException()
                .isThrownBy(() -> new RouteHandedOver(EVENT, AT, PLAN, DATE, ROUTE, VEHICLE, FROM, null));
    }

    @Test
    void GivenAMissingPart_WhenAPlanRevisedIsBuilt_ThenItIsRefused() {
        RevisionCause cause = RevisionCause.HANDED_OVER;
        List<RouteId> routes = List.of(ROUTE);

        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRevised(null, AT, PLAN, DATE, 2, cause, routes, none(), none(), none()));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRevised(EVENT, null, PLAN, DATE, 2, cause, routes, none(), none(), none()));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRevised(EVENT, AT, null, DATE, 2, cause, routes, none(), none(), none()));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRevised(EVENT, AT, PLAN, null, 2, cause, routes, none(), none(), none()));
        assertThatNullPointerException()
                .isThrownBy(() -> new PlanRevised(EVENT, AT, PLAN, DATE, 2, null, routes, none(), none(), none()));
    }

    @Test
    void GivenAWithdrawnVehicleWithoutItsCause_WhenAPlanRevisedIsBuilt_ThenItFailsAsABugInThePlan() {
        assertThatThrownBy(() -> new PlanRevised(
                        EVENT,
                        AT,
                        PLAN,
                        DATE,
                        2,
                        RevisionCause.REOPTIMIZATION_ACCEPTED,
                        List.of(ROUTE),
                        List.of(VEHICLE),
                        List.of(),
                        List.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void GivenEachWithdrawnVehicleWithItsCause_WhenAPlanRevisedIsBuilt_ThenTheyKeepTheirPositions() {
        VehicleId other = VehicleId.of(uuid(8));

        PlanRevised revised = new PlanRevised(
                EVENT,
                AT,
                PLAN,
                DATE,
                2,
                RevisionCause.REOPTIMIZATION_ACCEPTED,
                List.of(ROUTE),
                List.of(VEHICLE, other),
                List.of(WithdrawalCause.NO_DRIVER, WithdrawalCause.SHIFT_RAN_OUT),
                List.of());

        assertThat(revised.withdrawnVehicles()).containsExactly(VEHICLE, other);
        assertThat(revised.withdrawalCauses())
                .containsExactly(WithdrawalCause.NO_DRIVER, WithdrawalCause.SHIFT_RAN_OUT);
    }

    @Test
    void GivenAMissingPart_WhenAPlanPublishedOrPlanCancelledIsBuilt_ThenItIsRefused() {
        List<RouteId> routes = List.of(ROUTE);

        assertThatNullPointerException().isThrownBy(() -> new PlanPublished(null, AT, PLAN, DATE, 1, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanPublished(EVENT, null, PLAN, DATE, 1, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanPublished(EVENT, AT, null, DATE, 1, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanPublished(EVENT, AT, PLAN, null, 1, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanCancelled(null, AT, PLAN, DATE, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanCancelled(EVENT, null, PLAN, DATE, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanCancelled(EVENT, AT, null, DATE, routes));
        assertThatNullPointerException().isThrownBy(() -> new PlanCancelled(EVENT, AT, PLAN, null, routes));
    }

    private static <T> List<T> none() {
        return List.of();
    }

    private static UUID uuid(long n) {
        return new UUID(0x0190a5e27c3b7defL, n);
    }
}
