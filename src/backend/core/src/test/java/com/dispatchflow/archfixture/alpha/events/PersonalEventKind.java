package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.shared.PersonalData;

/**
 * An enum of the event's own package, which the allow-list admits, marked as personal data: only
 * the {@link PersonalData} check refuses it.
 */
public enum PersonalEventKind implements PersonalData {
    NAMED
}
