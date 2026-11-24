package com.dispatchflow.orders.application.api;

import com.dispatchflow.shared.GeoPoint;

/**
 * Whether a point lies inside the company's service area (FR-320). Customers &amp; Orders owns the
 * area, which the monthly road-data build produces. Planning asks it for each vehicle position it
 * sends to a re-optimization: ADR-050 has the core compute each position's in-service-area flag,
 * and the optimizer treats a position outside the area as unusable.
 *
 * <p>To Be Implemented.
 */
public interface ServiceAreaCheck {

    /**
     * Whether a point lies inside the current service area.
     *
     * <p>To Be Implemented.
     *
     * @param point the point to place, such as a vehicle's last position
     * @return {@code true} when the area contains the point
     */
    boolean contains(GeoPoint point);
}
