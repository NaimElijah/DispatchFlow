package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.archfixture.shared.KernelValue;
import java.time.Instant;

/** A clean event: the JDK, the kernel and its own package. */
public record AlphaHappened(KernelValue value, AlphaEventKind kind, Instant at) {}
