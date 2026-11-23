package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.FileHash;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link FileHash} as the text the upload adapter wrote. */
@Converter(autoApply = true)
public final class FileHashConverter extends StoredValueConverter<FileHash, String> {

    /** Creates the converter, which Hibernate does once. */
    public FileHashConverter() {
        super(FileHash.class, FileHash::value, FileHash::new);
    }
}
