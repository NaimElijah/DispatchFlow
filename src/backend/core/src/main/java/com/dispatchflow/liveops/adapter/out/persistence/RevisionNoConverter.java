package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link RevisionNo} as its number. A stored number below 1, which {@code RevisionNo}
 * refuses with a {@code RuleViolation}, is rebuilt as an incident, not a refusal (ADR-126).
 */
@Converter(autoApply = true)
public final class RevisionNoConverter extends StoredValueConverter<RevisionNo, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public RevisionNoConverter() {
        super(RevisionNo.class, RevisionNo::value, RevisionNo::of);
    }
}
