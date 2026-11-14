package com.dispatchflow.audit.domain;

/** Which app a client declares itself to be, in every call's {@code X-App} (NFR-93, ADR-082). */
public enum ClientApp {
    /** The web app, for every role. */
    WEB,
    /** The phone app, for every role. */
    PHONE
}
