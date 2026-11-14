package com.dispatchflow.orders.domain;

import static com.dispatchflow.orders.domain.CustomerBuilder.AREA;
import static com.dispatchflow.orders.domain.CustomerBuilder.INSIDE;
import static com.dispatchflow.orders.domain.CustomerBuilder.OUTSIDE;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.GeoPoint;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/** {@link ServiceArea#contains}, checked with JTS in longitude-latitude order (FR-320). */
class ServiceAreaTest {

    private static final GeometryFactory GEOMETRY = new GeometryFactory();

    private static Polygon square(double west, double south, double east, double north) {
        return GEOMETRY.createPolygon(new Coordinate[] {
            new Coordinate(west, south),
            new Coordinate(east, south),
            new Coordinate(east, north),
            new Coordinate(west, north),
            new Coordinate(west, south)
        });
    }

    @Test
    void GivenAPointInsideOrOutside_WhenChecked_ThenOnlyTheInsideOneIsContained() {
        assertThat(AREA.contains(INSIDE)).isTrue();
        assertThat(AREA.contains(OUTSIDE)).isFalse();
    }

    @Test
    void GivenAPointOnTheBoundary_WhenChecked_ThenItIsContained() {
        assertThat(AREA.contains(GeoPoint.of(32.20, 34.70))).isTrue();
    }

    @Test
    void GivenLatitudeAndLongitudeSwapped_WhenChecked_ThenThePointIsNotContained() {
        assertThat(AREA.contains(GeoPoint.of(34.78, 32.08))).isFalse();
    }

    @Test
    void GivenAnAreaOfTwoParts_WhenChecked_ThenAPointInEitherIsContainedAndOneBetweenIsNot() {
        ServiceArea twoParts = ServiceArea.of(GEOMETRY.createMultiPolygon(
                new Polygon[] {square(34.70, 32.00, 34.80, 32.10), square(35.10, 31.70, 35.30, 31.90)}));

        assertThat(twoParts.contains(GeoPoint.of(32.05, 34.75))).isTrue();
        assertThat(twoParts.contains(GeoPoint.of(31.78, 35.21))).isTrue();
        assertThat(twoParts.contains(GeoPoint.of(31.95, 34.95))).isFalse();
    }
}
