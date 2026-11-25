package com.dispatchflow.shared.adapter.out.uuid;

import com.dispatchflow.shared.FreshIds;
import com.dispatchflow.shared.application.IdGenerator;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The {@link IdGenerator} of production: UUID version 7 (RFC 9562), time-ordered, so new rows land
 * at the end of a primary key's index, made by {@code uuid-creator} because the JDK makes none.
 */
@Component
public final class UuidV7Generator implements IdGenerator {

    /** Creates the generator. */
    public UuidV7Generator() {}

    /**
     * One new UUID version 7.
     *
     * @return a UUID no other record has
     */
    @Override
    public UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * A supply of new UUIDs version 7.
     *
     * @param count how many IDs the supply holds, 0 or more
     * @return a supply of {@code count} new UUIDs
     * @throws IllegalArgumentException when {@code count} is negative
     */
    @Override
    public FreshIds fresh(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        return FreshIds.of(Stream.generate(this::next).limit(count).toList());
    }
}
