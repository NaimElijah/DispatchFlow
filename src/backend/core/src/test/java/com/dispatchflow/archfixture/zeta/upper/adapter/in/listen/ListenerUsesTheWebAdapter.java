package com.dispatchflow.archfixture.zeta.upper.adapter.in.listen;

import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.UpperRequest;

/** Rule 9: one inbound adapter uses another of its module. */
public record ListenerUsesTheWebAdapter(UpperRequest request) {}
