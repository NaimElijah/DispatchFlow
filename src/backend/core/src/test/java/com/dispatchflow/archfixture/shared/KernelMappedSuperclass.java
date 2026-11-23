package com.dispatchflow.archfixture.shared;

import jakarta.persistence.MappedSuperclass;

/** Allowed: the kernel carries persistence annotations too, as AggregateRoot does (ADR-126). */
@MappedSuperclass
public abstract class KernelMappedSuperclass {}
