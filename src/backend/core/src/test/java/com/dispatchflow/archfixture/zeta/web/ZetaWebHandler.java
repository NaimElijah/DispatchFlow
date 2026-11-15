package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;

/** Rule 2: {@code web} is no module, so the graph does not limit it. */
public record ZetaWebHandler(UpperUseCase upper, LowerFactsService lower) {}
