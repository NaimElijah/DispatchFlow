package com.dispatchflow.archfixture.beta.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Stands in for a module error test: it covers some of BetaError, not BetaOnly or SlotGone. Its
 * name matches no Surefire pattern, so these tests never run. The helper named after BetaOnly is not
 * a test, so it covers nothing.
 */
public final class BetaErrorRefusals {

    @Test
    void GivenAFullDate_WhenPlaced_ThenDateFull() {}

    @ParameterizedTest
    @ValueSource(ints = 1)
    void GivenAPastDate_WhenPlaced_ThenDateInPast(int daysAgo) {}

    @Test
    void GivenATakenSlot_WhenBooked_ThenSlotTaken() {}

    void GivenAnyRequest_WhenChecked_ThenBetaOnly() {}
}
