package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/** Rule 22: a use case takes a generated model, which must stop at the web adapter. */
public record UsesTheGeneratedModel(ThingDto thing) {}
