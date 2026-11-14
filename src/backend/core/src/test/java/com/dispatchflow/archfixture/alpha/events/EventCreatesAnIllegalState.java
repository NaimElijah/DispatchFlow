package com.dispatchflow.archfixture.alpha.events;

/** Rule 5 covers events too. */
public record EventCreatesAnIllegalState(int amount) {

    public EventCreatesAnIllegalState {
        if (amount < 0) {
            throw new IllegalStateException("negative");
        }
    }
}
