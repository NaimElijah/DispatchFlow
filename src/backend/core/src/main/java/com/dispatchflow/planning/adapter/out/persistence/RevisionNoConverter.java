package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link RevisionNo} as its number, in an {@code integer} column. A stored number below 1
 * is refused when read.
 */
@Converter(autoApply = true)
public final class RevisionNoConverter extends StoredValueConverter<RevisionNo, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public RevisionNoConverter() {
        super(RevisionNo.class, RevisionNo::value, RevisionNo::of);
    }
}
