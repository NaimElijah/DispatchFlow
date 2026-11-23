package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.session.SessionGeneration;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link SessionGeneration} as its count, which the token check compares (NFR-227). */
@Converter(autoApply = true)
public final class SessionGenerationConverter extends StoredValueConverter<SessionGeneration, Long> {

    /** Creates the converter, which Hibernate does once. */
    public SessionGenerationConverter() {
        super(SessionGeneration.class, SessionGeneration::value, SessionGeneration::of);
    }
}
