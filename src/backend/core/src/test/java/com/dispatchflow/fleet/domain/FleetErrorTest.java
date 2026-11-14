package com.dispatchflow.fleet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainError;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins Fleet's error codes, which both apps translate (NFR-144). That each error has a refusal test
 * is checked for every module by {@code ErrorRefusalTestsTest}.
 */
class FleetErrorTest {

    @Test
    void GivenTheFleetErrors_WhenTheirCodesAreListed_ThenTheyAreThePinnedCodes() {
        List<String> codes = Arrays.stream(FleetError.class.getPermittedSubclasses())
                .map(type -> DomainError.codeOf(type.asSubclass(DomainError.class)))
                .sorted()
                .toList();

        assertThat(codes)
                .containsExactly(
                        "BLANK_DEPOT_NAME",
                        "BLANK_DRIVER_NAME",
                        "BLANK_VEHICLE_NAME",
                        "DEPOT_HAS_ACTIVE_VEHICLES",
                        "DEPOT_IN_USE",
                        "DRIVER_ALREADY_HAS_VEHICLE",
                        "DRIVER_IN_USE",
                        "DRIVER_MARKED_UNAVAILABLE",
                        "DRIVER_NOT_ROSTERED",
                        "DUPLICATE_PLATE",
                        "DUPLICATE_VEHICLE_NAME",
                        "FLEET_FULL",
                        "INVALID_DEPOT_HOURS",
                        "INVALID_PLATE",
                        "INVALID_SHIFT",
                        "NOT_AN_ACTIVE_DRIVER",
                        "RETENTION_NOT_OVER",
                        "UNKNOWN_DEPOT",
                        "UNKNOWN_VEHICLE",
                        "VEHICLE_ALREADY_ASSIGNED",
                        "VEHICLE_IN_USE",
                        "VEHICLE_NOT_ACTIVE");
    }
}
