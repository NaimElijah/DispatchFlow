package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.web.adapter.out.streams.ZetaStreams;

/** Rule 10 allows it: web is no module, so its adapters stay usable. */
public record ClockUsesTheWebStreams(ZetaStreams streams) {}
