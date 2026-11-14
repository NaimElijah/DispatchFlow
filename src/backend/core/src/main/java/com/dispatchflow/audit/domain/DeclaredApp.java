package com.dispatchflow.audit.domain;

import java.util.Objects;

/**
 * The app and its version as the client declares them (NFR-93, ADR-082). Both travel on every call,
 * so a record holds both or, for a step no client made, neither (ADR-110).
 *
 * @param app which app the client says it is
 * @param version the version it says it runs
 */
public record DeclaredApp(ClientApp app, AppVersion version) {

    /** Checks both are present. */
    public DeclaredApp {
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(version, "version");
    }
}
