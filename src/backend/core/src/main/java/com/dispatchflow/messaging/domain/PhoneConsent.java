package com.dispatchflow.messaging.domain;

/** Whether a phone may get messages, by its opt-out history (FR-363, FR-380). */
public enum PhoneConsent {
    /** No opt-out stands. */
    MAY_BE_MESSAGED,
    /** The phone's latest entry is an opt-out: no further messages go to it. */
    OPTED_OUT
}
