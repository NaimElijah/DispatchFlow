package com.dispatchflow.shared.adapter.out.persistence.id;

import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link SessionId} that points to another aggregate as its {@code uuid}. A root's own
 * key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class SessionIdConverter extends StoredValueConverter<SessionId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public SessionIdConverter() {
        super(SessionId.class, SessionId::value, SessionId::of);
    }
}
