package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Version;

/** Rule: only AggregateRoot declares a @Version, on a getter as on a field. */
public class VersionsThroughAGetter {

    private long version;

    @Version
    public long getVersion() {
        return version;
    }
}
