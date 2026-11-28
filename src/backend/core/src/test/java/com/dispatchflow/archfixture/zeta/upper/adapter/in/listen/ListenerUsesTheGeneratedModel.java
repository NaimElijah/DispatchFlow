package com.dispatchflow.archfixture.zeta.upper.adapter.in.listen;

import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/** Rule 22: an inbound adapter other than web uses a generated model. */
public record ListenerUsesTheGeneratedModel(ThingDto thing) {}
