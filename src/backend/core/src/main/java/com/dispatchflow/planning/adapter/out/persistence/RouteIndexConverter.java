package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link RouteIndex} as its number, in an {@code integer} column. A stored index outside 1
 * to 30 is refused when read.
 */
@Converter(autoApply = true)
public final class RouteIndexConverter extends StoredValueConverter<RouteIndex, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public RouteIndexConverter() {
        super(RouteIndex.class, RouteIndex::value, RouteIndex::of);
    }
}
