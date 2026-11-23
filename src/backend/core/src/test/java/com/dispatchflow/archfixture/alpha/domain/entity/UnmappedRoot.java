package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;

/** Wrong on purpose: an aggregate root that is not an entity, so nothing can store it. */
public class UnmappedRoot extends AggregateRoot {

    protected UnmappedRoot() {}
}
