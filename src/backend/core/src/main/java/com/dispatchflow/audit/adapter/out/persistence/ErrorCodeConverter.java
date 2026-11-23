package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a refusal's {@link ErrorCode} as its upper snake case code (NFR-93, ADR-110). */
@Converter(autoApply = true)
public final class ErrorCodeConverter extends StoredValueConverter<ErrorCode, String> {

    /** Creates the converter, which Hibernate does once. */
    public ErrorCodeConverter() {
        super(ErrorCode.class, ErrorCode::value, ErrorCode::new);
    }
}
