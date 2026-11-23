package com.dispatchflow.access.adapter.out.persistence;

import com.dispatchflow.access.domain.TokenHash;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a {@link TokenHash}, all that is kept of a refresh or link token (NFR-91, NFR-211). */
@Converter(autoApply = true)
public final class TokenHashConverter extends StoredValueConverter<TokenHash, String> {

    /** Creates the converter, which Hibernate does once. */
    public TokenHashConverter() {
        super(TokenHash.class, TokenHash::value, TokenHash::new);
    }
}
