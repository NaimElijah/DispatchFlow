package com.dispatchflow.audit.application.api;

import java.util.Objects;

/**
 * The app and the version a client declared on a call, as the web adapter read them from {@code
 * X-App} and {@code X-App-Version} (NFR-93, ADR-082). The version is the text as sent: Audit parses
 * it, and a version that doesn't parse leaves the pair out of the record rather than refusing the
 * write (ADR-110).
 *
 * <p>To Be Implemented.
 *
 * @param kind which app the client says it is
 * @param version the version it says it runs, such as {@code 1.4.2}
 */
public record ClientRelease(ClientKind kind, String version) {

    /** Checks both parts are present. */
    public ClientRelease {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(version, "version");
    }
}
