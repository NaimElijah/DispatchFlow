package com.dispatchflow.archfixture.shared;

import com.dispatchflow.archfixture.shared.adapter.out.persistence.KernelAdapterUsesJpa;

/**
 * Rule 1: the kernel names none of the shared adapter's classes; the adapter depends on the kernel,
 * never the reverse (ADR-126).
 *
 * @param adapter the adapter it must not hold
 */
public record KernelUsesTheSharedAdapter(KernelAdapterUsesJpa adapter) {}
