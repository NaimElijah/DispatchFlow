package com.dispatchflow.planning.domain.drop;

import static com.dispatchflow.planning.domain.plan.PlanningFixtures.V1;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.at;
import static com.dispatchflow.planning.domain.plan.PlanningFixtures.dayAt;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.planning.domain.PinTarget;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DropReasonTest {

    @Test
    void GivenEachDropReason_WhenAskedWhetherItIsAboutTheLocation_ThenOnlyTheTwoLocationReasonsAre() {
        Weight kilograms = Weight.kilograms(BigDecimal.TEN);
        Volume litres = Volume.litres(BigDecimal.TEN);
        Map<Class<?>, DropReason> samples = new LinkedHashMap<>();
        Stream.of(
                        new DropReason.MissingLocation(),
                        new DropReason.NoVehicleAvailable(),
                        new DropReason.UnreachableLocation(),
                        new DropReason.RemovedByDispatcher(),
                        new DropReason.PinNotHonoured(new PinTarget.ToVehicle(V1), false),
                        new DropReason.Capacity(kilograms, litres, kilograms, litres),
                        new DropReason.TimeWindow(dayAt(30_000), at(31_000)),
                        new DropReason.Shift(dayAt(60_000), at(61_000)),
                        new DropReason.DepotHours(dayAt(60_000), at(61_000)),
                        new DropReason.VehicleLimit(),
                        new DropReason.Displaced())
                .forEach(reason -> samples.put(reason.getClass(), reason));
        Set<Class<?>> locationReasons = Set.of(DropReason.MissingLocation.class, DropReason.UnreachableLocation.class);

        assertThat(samples.keySet()).containsExactlyInAnyOrder(DropReason.class.getPermittedSubclasses());
        for (Class<?> variant : DropReason.class.getPermittedSubclasses()) {
            assertThat(samples.get(variant).isAboutLocation())
                    .as(variant.getSimpleName())
                    .isEqualTo(locationReasons.contains(variant));
        }
    }

    @Test
    void GivenALateShift_WhenADropsEarliestReturnPassesTheDay_ThenItKeepsTheSecondsFromTheDaysStart() {
        DropReason.Shift shift = new DropReason.Shift(dayAt(84_600), at(88_200));

        assertThat(shift.earliestReturn().seconds()).isEqualTo(88_200);
        assertThat(shift.earliestReturn().pastTheDay()).isTrue();
    }
}
