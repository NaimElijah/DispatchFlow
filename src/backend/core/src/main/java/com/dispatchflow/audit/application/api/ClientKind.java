package com.dispatchflow.audit.application.api;

/**
 * Which app a client says it is, from every call's {@code X-App} (NFR-93, ADR-082). Audit's domain
 * keeps its own copy, {@code ClientApp}.
 *
 * <p>Built in M0.
 */
public enum ClientKind {
    /** The web app, for every role. */
    WEB,
    /** The phone app, for every role. */
    PHONE
}
