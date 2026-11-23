package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.rating.Rating;
import com.dispatchflow.shared.adapter.out.persistence.StoredValueConverter;
import jakarta.persistence.Converter;

/** Stores a delivery's {@link Rating} as its number, 1 to 10. */
@Converter(autoApply = true)
public final class RatingConverter extends StoredValueConverter<Rating, Integer> {

    /** Creates the converter, which Hibernate does once. */
    public RatingConverter() {
        super(Rating.class, Rating::value, Rating::of);
    }
}
