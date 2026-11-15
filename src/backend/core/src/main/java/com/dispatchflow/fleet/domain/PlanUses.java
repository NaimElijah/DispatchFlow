package com.dispatchflow.fleet.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Every plan that uses one depot, vehicle or driver, read through {@code RecordUsage} and mapped
 * to Fleet's values before a retirement (DL §4.2). It decides which use blocks retiring: only an uncancelled plan for today
 * or a later date does (FR-305).
 *
 * @param uses the plans that use the record, in any order
 */
public record PlanUses(List<PlanUse> uses) {

    /** Copies the list. */
    public PlanUses {
        uses = List.copyOf(uses);
    }

    /**
     * No plan uses the record.
     *
     * @return the empty answer
     */
    public static PlanUses none() {
        return new PlanUses(List.of());
    }

    /**
     * Creates the answer.
     *
     * @param uses the plans that use the record
     * @return the answer
     */
    public static PlanUses of(List<PlanUse> uses) {
        return new PlanUses(uses);
    }

    /**
     * The use that blocks retiring the record today (FR-305): the earliest uncancelled plan dated
     * today or later. A date has at most one uncancelled plan (BR-34), so the earliest is unique.
     *
     * @param today today's date in {@code Asia/Jerusalem}
     * @return the blocking use, or empty when the record may be retired
     */
    public Optional<PlanUse> blockingOn(LocalDate today) {
        return uses.stream()
                .filter(use -> !use.cancelled() && !use.date().isBefore(today))
                .min(Comparator.comparing(PlanUse::date));
    }
}
