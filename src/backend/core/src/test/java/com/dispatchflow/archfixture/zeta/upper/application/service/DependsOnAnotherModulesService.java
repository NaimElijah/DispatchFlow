package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;

/** Rule 1: the application must not depend on another module's application beyond its api and spi. */
public record DependsOnAnotherModulesService(LowerFactsService service) {}
