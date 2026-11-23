package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Volume;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/** Stores a {@link Volume} as its litres, in a {@code numeric} column. */
@Converter(autoApply = true)
public final class VolumeConverter extends StoredValueConverter<Volume, BigDecimal> {

    /** Creates the converter, which Hibernate does once. */
    public VolumeConverter() {
        super(Volume.class, Volume::litres, Volume::litres);
    }
}
