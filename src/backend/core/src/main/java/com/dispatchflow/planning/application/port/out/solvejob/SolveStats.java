package com.dispatchflow.planning.application.port.out.solvejob;

import java.time.Duration;
import java.util.Objects;

/**
 * How the optimizer's search ran, from its answer's stats, so the solve is recorded with its solver
 * version and timings and can be replayed under the budget it reached (NFR-11, NFR-13, ADR-044). The
 * solver's name, the limit and the seed are the job's own, so they are not repeated here.
 *
 * <p>Built in M0.
 *
 * @param solverVersion the solver's version, as the optimizer reports it
 * @param reached the budget that replays the search: the iterations or the solutions it reached
 * @param searchTime how long the search ran, by the optimizer's stopwatch
 */
public record SolveStats(String solverVersion, Reached reached, Duration searchTime) {

    /** Checks every part is present. */
    public SolveStats {
        Objects.requireNonNull(solverVersion, "solverVersion");
        Objects.requireNonNull(reached, "reached");
        Objects.requireNonNull(searchTime, "searchTime");
    }

    /**
     * What a replay budget counts, as the solver counts it (ADR-044).
     *
     * <p>Built in M0.
     */
    public enum Counted {
        /** The search's iterations. */
        ITERATIONS,
        /** The solutions the search found. */
        SOLUTIONS
    }

    /**
     * The budget a search reached, which replays it (NFR-13, NFR-115).
     *
     * <p>Built in M0.
     *
     * @param counted what the count counts
     * @param count how many it reached
     */
    public record Reached(Counted counted, long count) {

        /** Checks what is counted is present. */
        public Reached {
            Objects.requireNonNull(counted, "counted");
        }
    }
}
