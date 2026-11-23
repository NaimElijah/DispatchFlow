package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link HeldReportId} that an outcome or an alert names as its {@code uuid}. A held
 * report's own key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class HeldReportIdConverter extends StoredValueConverter<HeldReportId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public HeldReportIdConverter() {
        super(HeldReportId.class, HeldReportId::value, HeldReportId::of);
    }
}
