package com.dispatchflow.shared;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.SharedKernelError.InvalidCoordinates;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoPointTest {

    @ParameterizedTest
    @CsvSource({"32.0853, 34.7818", "90, 180", "-90, -180", "0, 0"})
    void GivenCoordinatesInRange_WhenBuilt_ThenTheyAreKept(double latitude, double longitude) {
        GeoPoint point = GeoPoint.of(latitude, longitude);

        assertThat(point.latitude()).isEqualTo(latitude);
        assertThat(point.longitude()).isEqualTo(longitude);
    }

    @ParameterizedTest
    @CsvSource({
        "90.000001, 0",
        "-90.000001, 0",
        "0, 180.000001",
        "0, -180.000001",
        "NaN, 34.7818",
        "32.0853, NaN",
        "Infinity, 0",
        "0, -Infinity"
    })
    void GivenCoordinatesOutOfRange_WhenBuilt_ThenInvalidCoordinates(double latitude, double longitude) {
        assertRefused(() -> GeoPoint.of(latitude, longitude), new InvalidCoordinates());
    }
}
