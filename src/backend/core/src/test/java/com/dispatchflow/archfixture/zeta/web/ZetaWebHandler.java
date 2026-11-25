package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;

/**
 * Rule 2: {@code web} is no module, so the graph does not limit it. Rule 13 flags it all the same:
 * it uses two modules' services, which are neither their {@code api} nor their {@code events}.
 */
public record ZetaWebHandler(UpperUseCase upper, LowerFactsService lower) {}
