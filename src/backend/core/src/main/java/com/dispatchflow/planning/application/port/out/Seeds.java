package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.plan.Seed;

/**
 * The seeded generator a solve's seed comes from, so the same input, seed and budget give the same
 * solution, and a test controls it (NFR-13, TS §2.1 rule 3, ADR-044). The domain never makes one.
 */
public interface Seeds {

    /**
     * The next seed.
     *
     * @return a seed for a new solve job
     */
    Seed next();
}
