package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/**
 * Stores a client's declared {@link AppVersion} as a client writes it, such as {@code 1.4.2}
 * (NFR-93, ADR-082), so an Admin reading the trail sees the version the call declared.
 */
@Converter(autoApply = true)
public final class AppVersionConverter extends StoredValueConverter<AppVersion, String> {

    /** Creates the converter, which Hibernate does once. */
    public AppVersionConverter() {
        super(AppVersion.class, AppVersion::text, AppVersion::parse);
    }
}
