package com.dispatchflow.archfixture.zeta;

import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;
import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;

/** Rule 2: a class in the root's own package belongs to no module, so it may wire any of them. */
public record ZetaApplication(UpperUseCase upper, LowerFactsService lower) {}
