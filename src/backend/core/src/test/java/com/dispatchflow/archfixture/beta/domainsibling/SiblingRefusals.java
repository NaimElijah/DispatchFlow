package com.dispatchflow.archfixture.beta.domainsibling;

import org.junit.jupiter.api.Test;

/**
 * A refusal test for BetaError's SlotMissed in {@code beta.domainsibling}, whose name starts with
 * the error's package name but is not under it, so it covers nothing. Its name matches no Surefire
 * pattern, so it never runs.
 */
public final class SiblingRefusals {

    @Test
    void GivenAMissedSlot_WhenBooked_ThenSlotMissed() {}
}
