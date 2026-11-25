package com.dispatchflow.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.architecture.ProductionClasses;
import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.planning.domain.BasisChange;
import com.dispatchflow.planning.domain.PlanningError;
import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SharedKernelError;
import com.dispatchflow.shared.TextKind;
import com.dispatchflow.shared.TimeWindow;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ErrorParamsTest {

    private static final UUID FIRST = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final UUID SECOND = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcdf");
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2026, 3, 15));

    @Test
    void GivenEveryProductionError_WhenItsComponentTypesAreRead_ThenEachHasAMapping() {
        List<RecordComponent> components = ProductionClasses.ALL.stream()
                .filter(type -> type.isAssignableTo(DomainError.class))
                .filter(type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .map(type -> type.reflect())
                .flatMap(type -> Stream.of(type.getRecordComponents()))
                .toList();

        assertThat(components)
                .isNotEmpty()
                .allSatisfy(component -> assertThat(ErrorParams.isMappable(component.getGenericType()))
                        .as("%s.%s", component.getDeclaringRecord().getName(), component.getName())
                        .isTrue());
    }

    @Test
    void GivenEveryProductionError_WhenItsTypeIsRead_ThenItIsARecord() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type -> type.isAssignableTo(DomainError.class))
                        .filter(type ->
                                !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT)))
                .isNotEmpty()
                .allSatisfy(
                        type -> assertThat(type.isRecord()).as(type.getName()).isTrue());
    }

    @Test
    void GivenAnErrorWithAPresentAndAnEmptyOptional_WhenMapped_ThenThePresentOneIsUnwrappedAndTheEmptyOneLeftOut() {
        PlanningError.OpenViolation violation =
                new PlanningError.OpenViolation(RuleCode.SHIFTS, Optional.of(RouteId.of(FIRST)), Optional.empty());

        assertThat(ErrorParams.of(violation)).isEqualTo(Map.of("rule", "BR-06", "route", FIRST.toString()));
    }

    @Test
    void GivenAnErrorWithATimeWindow_WhenMapped_ThenTheWindowIsAnObjectOfItsLocalTimes() {
        TimeWindow window = TimeWindow.on(DATE, LocalTime.of(9, 0), LocalTime.of(12, 30));

        assertThat(ErrorParams.of(new OrdersError.WindowNotOnDate(window, DATE)))
                .isEqualTo(Map.of(
                        "window",
                        Map.of("start", "2026-03-15T09:00:00", "end", "2026-03-15T12:30:00"),
                        "date",
                        "2026-03-15"));
    }

    @Test
    void GivenAnErrorListingSealedVariants_WhenMapped_ThenEachVariantNamesItsKind() {
        PlanningError.ProposalOutdated outdated = new PlanningError.ProposalOutdated(
                ProposalId.of(FIRST), List.of(new BasisChange.CancelledOrder(OrderId.of(SECOND))));

        assertThat(ErrorParams.of(outdated))
                .isEqualTo(Map.of(
                        "proposal",
                        FIRST.toString(),
                        "changes",
                        List.of(Map.of("kind", "CANCELLED_ORDER", "order", SECOND.toString()))));
    }

    @Test
    void GivenAnErrorWithAnEnum_WhenMapped_ThenItsName() {
        assertThat(ErrorParams.of(new SharedKernelError.TextHasForbiddenCharacters(TextKind.ADDRESS)))
                .isEqualTo(Map.of("kind", "ADDRESS"));
    }

    @Test
    void GivenAnErrorWithAnEmptyList_WhenMapped_ThenTheEmptyListIsKept() {
        assertThat(ErrorParams.of(new PlanningError.ProposalOutdated(ProposalId.of(FIRST), List.of())))
                .isEqualTo(Map.of("proposal", FIRST.toString(), "changes", List.of()));
    }

    @Test
    void GivenAnErrorHoldingAPersonalValue_WhenMapped_ThenIllegalStateExceptionThatQuotesNoValue() {
        assertThatThrownBy(() -> ErrorParams.of(new LeaksAName(new Name("Dana Levi"))))
                .isInstanceOf(IllegalStateException.class)
                .message()
                .doesNotContain("Dana");
    }

    @Test
    void GivenAnErrorHoldingATypeWithNoMapping_WhenMapped_ThenIllegalStateException() {
        assertThatThrownBy(() -> ErrorParams.of(new HoldsASet(Set.of(FIRST))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void GivenTypesWithNoMapping_WhenTheirMappingIsAsked_ThenNone() {
        assertThat(Arrays.stream(new Type[] {
                    Object.class,
                    Name.class,
                    HoldsASet.class.getRecordComponents()[0].getGenericType(),
                    List.class,
                    Optional.class
                }))
                .noneMatch(ErrorParams::isMappable);
    }

    @Test
    void GivenAOneComponentRecordWrappingAnOptional_WhenItsMappingIsAsked_ThenNone() {
        assertThat(ErrorParams.isMappable(HoldsAMaybe.class.getRecordComponents()[0].getGenericType()))
                .isFalse();
        assertThatThrownBy(() -> ErrorParams.of(new HoldsAMaybe(new MaybeRoute(Optional.of(FIRST)))))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A personal value, as a module's would be: its toString() redacts it. */
    record Name(String value) implements PersonalData {
        @Override
        public String toString() {
            return "Name[redacted]";
        }
    }

    /** An error the shape rules would refuse: it holds a personal value. */
    record LeaksAName(Name name) implements DomainError {}

    /** An error the shape rules would refuse: a set has no mapping. */
    record HoldsASet(Set<UUID> ids) implements DomainError {}

    /** A one-part value whose part is optional, which a param can't unwrap. */
    record MaybeRoute(Optional<UUID> route) {}

    /** An error holding that value. */
    record HoldsAMaybe(MaybeRoute maybe) implements DomainError {}
}
