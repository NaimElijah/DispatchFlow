package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.RuleCode;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link RuleCode} as its row ID, in a {@code text} column. A stored code that is no row ID
 * is refused when read.
 */
@Converter(autoApply = true)
public final class RuleCodeConverter extends StoredValueConverter<RuleCode, String> {

    /** Creates the converter, which Hibernate does once. */
    public RuleCodeConverter() {
        super(RuleCode.class, RuleCode::value, RuleCode::of);
    }
}
