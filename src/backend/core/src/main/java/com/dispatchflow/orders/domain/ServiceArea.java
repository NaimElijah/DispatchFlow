package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.GeoPoint;
import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/**
 * The company's area (FR-320): the road-data extract's boundary polygon, or the company's part of
 * it, which the monthly road-data build produces and the application layer loads. Its one question
 * is whether a point lies inside, checked in memory with JTS (TS §9, the service area row).
 *
 * <p>Coordinates are WGS 84 degrees, with longitude as JTS's x and latitude as its y. A point on the
 * boundary counts as inside.
 */
public final class ServiceArea {

    private final PreparedGeometry boundary;
    private final GeometryFactory points;

    private ServiceArea(Geometry boundary) {
        // A JTS geometry is mutable, so the area prepares its own copy and the caller can't change it.
        this.boundary = PreparedGeometryFactory.prepare(boundary.copy());
        this.points = boundary.getFactory();
    }

    /**
     * Creates the area from its boundary.
     *
     * @param boundary a polygon or a multipolygon, in longitude and latitude
     * @param <G> a JTS geometry that is polygonal, so a line or a point can't be passed
     * @return the area
     */
    public static <G extends Geometry & Polygonal> ServiceArea of(G boundary) {
        return new ServiceArea(Objects.requireNonNull(boundary, "boundary"));
    }

    /**
     * Whether a point lies inside the area or on its boundary (FR-320).
     *
     * @param point the point
     * @return {@code true} when inside
     */
    public boolean contains(GeoPoint point) {
        return boundary.covers(points.createPoint(new Coordinate(point.longitude(), point.latitude())));
    }
}
