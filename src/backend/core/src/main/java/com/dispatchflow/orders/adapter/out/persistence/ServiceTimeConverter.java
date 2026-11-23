package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.ServiceTime;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link ServiceTime} as its whole seconds, in a {@code bigint} column. */
@Converter(autoApply = true)
public final class ServiceTimeConverter extends StoredValueConverter<ServiceTime, Long> {

    /** Creates the converter, which Hibernate does once. */
    public ServiceTimeConverter() {
        super(ServiceTime.class, ServiceTime::seconds, ServiceTime::ofSeconds);
    }
}
