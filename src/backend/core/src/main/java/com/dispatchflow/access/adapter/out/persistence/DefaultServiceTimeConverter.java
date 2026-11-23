package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.companyprofile.DefaultServiceTime;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link DefaultServiceTime} as its whole seconds (FR-184). */
@Converter(autoApply = true)
public final class DefaultServiceTimeConverter extends StoredValueConverter<DefaultServiceTime, Long> {

    /** Creates the converter, which Hibernate does once. */
    public DefaultServiceTimeConverter() {
        super(DefaultServiceTime.class, DefaultServiceTime::seconds, DefaultServiceTime::new);
    }
}
