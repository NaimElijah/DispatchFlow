package com.dispatchflow.archfixture.beta.domain;

/** Stands in for a module error test: it covers some of BetaError, not BetaOnly or SlotGone. */
public final class BetaErrorRefusals {

    void GivenAFullDate_WhenPlaced_ThenDateFull() {}

    void GivenAPastDate_WhenPlaced_ThenDateInPast() {}

    void GivenATakenSlot_WhenBooked_ThenSlotTaken() {}
}
