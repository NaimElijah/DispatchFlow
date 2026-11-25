package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;
import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.shared.adapter.out.clock.ZetaClock;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperFacts;
import com.dispatchflow.archfixture.zeta.web.adapter.out.streams.ZetaStreams;

/**
 * Rule 13: {@code web} calls a module's {@code api} and hears another's event, and uses the kernel,
 * the kernel's adapters and its own classes freely, so nothing is flagged.
 */
public record ZetaWebEndpoint(
        UpperFacts upper, LowerHappened heard, ZetaKernelValue value, ZetaClock clock, ZetaStreams streams) {}
