package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.UpperTable;

/** Rule 9: an inbound adapter uses an outbound one of its module, not a port. */
public record InboundUsesTheStore(UpperTable table) {}
