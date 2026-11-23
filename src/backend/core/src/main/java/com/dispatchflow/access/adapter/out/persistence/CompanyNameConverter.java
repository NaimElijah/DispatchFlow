package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.companyprofile.CompanyName;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link CompanyName} as its normalized text, rebuilt through {@link CompanyName#ofStored}. */
@Converter(autoApply = true)
public final class CompanyNameConverter extends StoredValueConverter<CompanyName, String> {

    /** Creates the converter, which Hibernate does once. */
    public CompanyNameConverter() {
        super(CompanyName.class, CompanyName::value, CompanyName::ofStored);
    }
}
