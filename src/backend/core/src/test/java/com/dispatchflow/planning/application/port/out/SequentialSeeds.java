package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.plan.Seed;

/** {@link Seeds} a test can name before it calls: the first seed it is given, then one more each time. */
public final class SequentialSeeds implements Seeds {

    private long nextValue;

    /**
     * Creates the seeds.
     *
     * @param first the value of the first seed drawn
     */
    public SequentialSeeds(long first) {
        this.nextValue = first;
    }

    @Override
    public Seed next() {
        Seed seed = Seed.of(nextValue);
        nextValue++;
        return seed;
    }
}
