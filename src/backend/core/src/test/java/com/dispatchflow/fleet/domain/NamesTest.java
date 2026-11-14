package com.dispatchflow.fleet.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.fleet.domain.FleetError.BlankDepotName;
import com.dispatchflow.fleet.domain.FleetError.BlankDriverName;
import com.dispatchflow.fleet.domain.FleetError.BlankVehicleName;
import com.dispatchflow.shared.PhoneNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NamesTest {

    @Test
    void GivenANameWithSurroundingSpaces_WhenRead_ThenItIsTrimmed() {
        assertThat(VehicleName.of("  Truck 1 ").value()).isEqualTo("Truck 1");
        assertThat(DepotName.of(" North ").value()).isEqualTo("North");
        assertThat(DriverName.of(" Dana Levi ").value()).isEqualTo("Dana Levi");
    }

    @Test
    void GivenTwoNamesThatDifferOnlyInSurroundingSpaces_WhenCompared_ThenTheyAreEqual() {
        assertThat(VehicleName.of("Truck 1")).isEqualTo(VehicleName.of(" Truck 1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void GivenABlankVehicleName_WhenRead_ThenBlankVehicleName(String text) {
        assertRefused(() -> VehicleName.of(text), new BlankVehicleName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void GivenABlankDepotName_WhenRead_ThenBlankDepotName(String text) {
        assertRefused(() -> DepotName.of(text), new BlankDepotName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void GivenABlankDriverName_WhenRead_ThenBlankDriverName(String text) {
        assertRefused(() -> DriverName.of(text), new BlankDriverName());
    }

    @Test
    void GivenADriversNameAndPhone_WhenPrinted_ThenNeitherAppears() {
        DriverContact contact = new DriverContact(DriverName.of("Dana Levi"), PhoneNumber.parse("052-345-6789"));

        assertThat(DriverName.of("Dana Levi").toString()).doesNotContain("Dana");
        assertThat(contact.toString()).doesNotContain("Dana").doesNotContain("345");
    }
}
