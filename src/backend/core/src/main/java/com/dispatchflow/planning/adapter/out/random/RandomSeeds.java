package com.dispatchflow.planning.adapter.out.random;

import com.dispatchflow.planning.application.port.out.Seeds;
import com.dispatchflow.planning.domain.plan.Seed;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Draws each solve job's seed from the thread's own random generator. Any {@code long} is a seed,
 * so every draw is one.
 *
 * <p>A seed needs no secrecy: it only fixes the solver's search, and the job stores the seed it ran
 * with, so the same input, seed and budget give the same solution when a solve is run again
 * (NFR-13). Reproducing a solve takes its stored seed, never this generator's state, so the seed
 * need not be predictable, and {@code SecureRandom} would add nothing.
 */
@Component
final class RandomSeeds implements Seeds {

    @Override
    public Seed next() {
        return Seed.of(ThreadLocalRandom.current().nextLong());
    }
}
