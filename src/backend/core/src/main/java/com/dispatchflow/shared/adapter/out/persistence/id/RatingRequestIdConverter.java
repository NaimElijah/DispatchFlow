package com.dispatchflow.shared.adapter.out.persistence.id;

import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link RatingRequestId} that points to another aggregate as its {@code uuid}. A root's own
 * key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class RatingRequestIdConverter extends StoredValueConverter<RatingRequestId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public RatingRequestIdConverter() {
        super(RatingRequestId.class, RatingRequestId::value, RatingRequestId::of);
    }
}
