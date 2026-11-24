package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.ServiceArea;

/**
 * The company's service area, which the monthly road-data build produces (FR-320): the company's
 * area within the OpenStreetMap extract, or the whole extract until a company is known. A
 * hand-placed location must lie inside it, a geocoded one outside it is flagged (FR-63), and
 * Planning asks whether a vehicle's position lies inside it (ADR-050).
 *
 * <p>To Be Implemented.
 */
public interface ServiceAreas {

    /**
     * The service area now.
     *
     * <p>To Be Implemented.
     *
     * @return the area of the current road-data build
     */
    ServiceArea current();
}
