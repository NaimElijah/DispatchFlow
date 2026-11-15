package com.dispatchflow.reporting.domain;

import com.dispatchflow.shared.OrderId;
import java.util.List;
import java.util.Objects;

/** Why a baseline was last computed, which decides what it is compared on (DL §4.7, FR-269). */
public sealed interface BaselineCause {

    /** The plan was published, and no revision has withdrawn a vehicle or added an order since (FR-39). */
    record Published() implements BaselineCause {}

    /**
     * Revisions withdrew vehicles mid-route or added orders, so the baseline was solved again after
     * them, on the same orders and fleet as the plan (FR-39, FR-267, FR-270, ADR-109).
     *
     * @param withdrawn every vehicle withdrawn so far with its cause, each vehicle once, in the
     *     order first withdrawn (FR-271)
     * @param added every order added so far, each once, in the order first added
     */
    record Recomputed(List<Withdrawal> withdrawn, List<OrderId> added) implements BaselineCause {

        /** Copies the lists; {@link Baseline} checks that at least one names something. */
        public Recomputed {
            withdrawn = List.copyOf(Objects.requireNonNull(withdrawn, "withdrawn"));
            added = List.copyOf(Objects.requireNonNull(added, "added"));
        }
    }
}
