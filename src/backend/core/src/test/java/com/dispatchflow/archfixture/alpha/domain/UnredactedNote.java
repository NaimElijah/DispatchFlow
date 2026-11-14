package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.PersonalData;

/** ADR-106: a personal class that inherits Object's toString instead of declaring its own. */
public final class UnredactedNote implements PersonalData {

    private final String text;

    public UnredactedNote(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
