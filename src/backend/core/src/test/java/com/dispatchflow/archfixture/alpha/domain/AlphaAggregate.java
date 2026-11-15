package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Version;

/** An aggregate whose restore only persistence may call; reloaded() shows it may call its own. */
public final class AlphaAggregate extends AggregateRoot {

    private AlphaAggregate(Version version) {
        super(version);
    }

    public static AlphaAggregate restore(long version) {
        return new AlphaAggregate(Version.of(version));
    }

    static AlphaAggregate reloaded() {
        return restore(1);
    }
}
