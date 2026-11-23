package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a request's {@link TraceId} as its 32 hex digits (NFR-93, NFR-20). */
@Converter(autoApply = true)
public final class TraceIdConverter extends StoredValueConverter<TraceId, String> {

    /** Creates the converter, which Hibernate does once. */
    public TraceIdConverter() {
        super(TraceId.class, TraceId::value, TraceId::of);
    }
}
