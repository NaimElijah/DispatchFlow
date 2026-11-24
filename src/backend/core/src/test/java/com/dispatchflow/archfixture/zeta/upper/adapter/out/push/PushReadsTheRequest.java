package com.dispatchflow.archfixture.zeta.upper.adapter.out.push;

import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.UpperRequest;

/** Rule 9: an outbound adapter uses an inbound one of its module. */
public record PushReadsTheRequest(UpperRequest request) {}
