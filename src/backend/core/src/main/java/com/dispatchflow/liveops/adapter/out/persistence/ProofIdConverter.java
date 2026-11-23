package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** Stores a {@link ProofId}, the uploaded file a proof names, as its {@code uuid}. */
@Converter(autoApply = true)
public final class ProofIdConverter extends StoredValueConverter<ProofId, UUID> {

    /** Creates the converter, which Hibernate does once. */
    public ProofIdConverter() {
        super(ProofId.class, ProofId::value, ProofId::of);
    }
}
