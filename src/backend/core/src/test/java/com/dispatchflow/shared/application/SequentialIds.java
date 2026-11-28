package com.dispatchflow.shared.application;

import com.dispatchflow.shared.FreshIds;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * An {@link IdGenerator} whose IDs a test can name before it calls: {@code new UUID(prefix, 1)},
 * then 2, and so on, across {@link #next()} and {@link #fresh(int)} alike.
 */
public final class SequentialIds implements IdGenerator {

    private final long prefix;
    private long last;

    /**
     * Creates the generator.
     *
     * @param prefix the most significant bits of every ID it makes
     */
    public SequentialIds(long prefix) {
        this.prefix = prefix;
    }

    /**
     * The ID a call makes, counted from the first.
     *
     * @param prefix the generator's prefix
     * @param n which ID, from 1
     * @return the {@code n}th ID a generator with that prefix makes
     */
    public static UUID nth(long prefix, long n) {
        return new UUID(prefix, n);
    }

    @Override
    public UUID next() {
        last++;
        return nth(prefix, last);
    }

    @Override
    public FreshIds fresh(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be 0 or more: " + count);
        }
        return FreshIds.of(Stream.generate(this::next).limit(count).toList());
    }
}
