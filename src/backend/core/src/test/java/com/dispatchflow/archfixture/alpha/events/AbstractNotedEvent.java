package com.dispatchflow.archfixture.alpha.events;

/** A supertype that carries free text into the events that extend it. */
public abstract class AbstractNotedEvent {

    protected final String note;

    protected AbstractNotedEvent(String note) {
        this.note = note;
    }
}
