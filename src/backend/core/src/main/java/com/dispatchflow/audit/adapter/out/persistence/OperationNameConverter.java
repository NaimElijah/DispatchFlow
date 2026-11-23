package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores an audited {@link OperationName} as its identifier (NFR-93). */
@Converter(autoApply = true)
public final class OperationNameConverter extends StoredValueConverter<OperationName, String> {

    /** Creates the converter, which Hibernate does once. */
    public OperationNameConverter() {
        super(OperationName.class, OperationName::value, OperationName::of);
    }
}
