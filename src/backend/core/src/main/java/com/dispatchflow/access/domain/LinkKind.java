package com.dispatchflow.access.domain;

import java.time.Duration;

/** The kinds of credential link, each with how long it works after it is sent (DL §4.1). */
public enum LinkKind {
    /** A new user's first password: works for 72 hours (FR-294). */
    SET_PASSWORD(Duration.ofHours(72)),
    /** A forgotten password: works for 1 hour (FR-345). */
    RECOVERY(Duration.ofHours(1)),
    /** A new email address: works for 1 hour (FR-388). */
    EMAIL_CHANGE(Duration.ofHours(1));

    private final Duration lifetime;

    LinkKind(Duration lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * How long a link of this kind works after it is sent.
     *
     * @return the lifetime
     */
    public Duration lifetime() {
        return lifetime;
    }
}
