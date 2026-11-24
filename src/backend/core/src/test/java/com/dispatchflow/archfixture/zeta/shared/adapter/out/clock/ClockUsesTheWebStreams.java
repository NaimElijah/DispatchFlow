package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.web.adapter.out.streams.ZetaStreams;

/**
 * Rules 9 and 10 allow it, web being no module; rule 11 refuses it, because web may reach any
 * module (ADR-131).
 */
public record ClockUsesTheWebStreams(ZetaStreams streams) {}
