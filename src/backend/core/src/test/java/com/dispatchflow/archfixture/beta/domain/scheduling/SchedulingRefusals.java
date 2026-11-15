package com.dispatchflow.archfixture.beta.domain.scheduling;

import org.junit.jupiter.api.Test;

/**
 * Stands in for a refusal test in a subpackage of its error's package (ADR-118 rule 10): it covers
 * BetaError's SlotHeld from {@code beta.domain.scheduling}. Its name matches no Surefire pattern, so
 * it never runs.
 */
public final class SchedulingRefusals {

    @Test
    void GivenAHeldSlot_WhenBooked_ThenSlotHeld() {}
}
