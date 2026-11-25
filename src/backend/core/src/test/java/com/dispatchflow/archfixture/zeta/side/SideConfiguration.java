package com.dispatchflow.archfixture.zeta.side;

import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: a configuration that imports another module's service. Rule 2 lets side depend on
 * lower, so only rule 15 flags it.
 */
@Import(LowerFactsService.class)
class SideConfiguration {}
