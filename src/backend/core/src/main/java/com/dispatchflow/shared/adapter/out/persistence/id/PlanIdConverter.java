package com.dispatchflow.shared.adapter.out.persistence.id;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link PlanId} that points to another aggregate as its {@code uuid}. A root's own
 * key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class PlanIdConverter extends StoredValueConverter<PlanId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public PlanIdConverter() {
        super(PlanId.class, PlanId::value, PlanId::of);
    }
}
