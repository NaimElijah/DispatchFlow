package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.shared.clientapi.model.ThingDto;

/** Rule 22: {@code web} uses a generated model, which it may. */
public record WebAnswersWithTheGeneratedModel(ThingDto thing) {}
