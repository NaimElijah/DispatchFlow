package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Stores a {@link Buffer} as its percent, in a {@code numeric} column. A stored negative buffer is
 * refused when read.
 */
@Converter(autoApply = true)
public final class BufferConverter extends StoredValueConverter<Buffer, BigDecimal> {

    /** Creates the converter, which Hibernate does once. */
    public BufferConverter() {
        super(Buffer.class, Buffer::percent, Buffer::percent);
    }
}
