package com.dispatchflow.archfixture.alpha.domain.mapping;

import com.dispatchflow.archfixture.shared.adapter.out.persistence.KernelAdapterUsesJpa;

/**
 * Rule 1: a domain uses no shared adapter, a converter included: its mapping relies on the
 * auto-applied ones (ADR-126).
 *
 * @param adapter the adapter it must not hold
 */
public record UsesTheSharedAdapter(KernelAdapterUsesJpa adapter) {}
