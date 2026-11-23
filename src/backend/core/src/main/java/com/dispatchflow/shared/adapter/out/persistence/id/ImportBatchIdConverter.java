package com.dispatchflow.shared.adapter.out.persistence.id;

import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/**
 * Stores a {@link ImportBatchId} that points to another aggregate as its {@code uuid}. A root's own
 * key is a plain {@code UUID} field, which no converter touches (ADR-126).
 */
@Converter(autoApply = true)
public final class ImportBatchIdConverter extends StoredValueConverter<ImportBatchId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public ImportBatchIdConverter() {
        super(ImportBatchId.class, ImportBatchId::value, ImportBatchId::of);
    }
}
