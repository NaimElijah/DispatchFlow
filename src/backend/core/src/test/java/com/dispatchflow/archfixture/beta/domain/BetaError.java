package com.dispatchflow.archfixture.beta.domain;

import com.dispatchflow.shared.DomainError;

/** DATE_FULL repeats AlphaError's with no allow-list entry; DATE_IN_PAST is allow-listed. */
public sealed interface BetaError extends DomainError {

    record DateFull() implements BetaError {}

    record DateInPast() implements BetaError {}

    record BetaOnly() implements BetaError {}

    /** A nested sealed group, whose errors must be found by walking the permitted subtypes. */
    sealed interface Scheduling extends BetaError {

        record SlotTaken() implements Scheduling {}

        record SlotGone() implements Scheduling {}
    }
}
