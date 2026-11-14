package com.dispatchflow.messaging.domain;

/** One of the two switches an Admin turns: messages to recipients (FR-350) and the rating question (FR-375). */
public enum Switch {
    /** Turned on. */
    ON,
    /** Turned off, as both start (FR-350, FR-375). */
    OFF
}
