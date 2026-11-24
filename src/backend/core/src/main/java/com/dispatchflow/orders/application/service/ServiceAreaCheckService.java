package com.dispatchflow.orders.application.service;

import com.dispatchflow.orders.application.api.ServiceAreaCheck;
import com.dispatchflow.orders.application.port.out.ServiceAreas;
import com.dispatchflow.shared.GeoPoint;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers &amp; Orders' implementation of {@link ServiceAreaCheck}: it asks the current {@code
 * ServiceArea} whether it contains a point, so Planning can flag each position it sends to a
 * re-optimization (FR-320, ADR-050).
 *
 * <p>Built in M9.
 */
@Transactional(readOnly = true)
public class ServiceAreaCheckService implements ServiceAreaCheck {

    private final ServiceAreas serviceAreas;

    /**
     * Creates the service.
     *
     * @param serviceAreas the company's service area
     */
    public ServiceAreaCheckService(ServiceAreas serviceAreas) {
        this.serviceAreas = serviceAreas;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M9.
     */
    @Override
    public boolean contains(GeoPoint point) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
