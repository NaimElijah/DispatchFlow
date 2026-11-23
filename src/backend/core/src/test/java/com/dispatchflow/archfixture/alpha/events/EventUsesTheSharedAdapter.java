package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.archfixture.shared.adapter.out.persistence.KernelAdapterUsesJpa;

/**
 * Rule 1: an events package uses no shared adapter (ADR-126).
 *
 * @param adapter the adapter it must not hold
 */
public record EventUsesTheSharedAdapter(KernelAdapterUsesJpa adapter) {}
