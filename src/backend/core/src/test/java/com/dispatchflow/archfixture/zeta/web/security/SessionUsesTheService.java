package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;

/**
 * Rule 13: {@code web} uses a module's service, from a package below {@code web}, which is neither
 * its {@code api} nor its {@code events}.
 */
public record SessionUsesTheService(UpperUseCase upper) {}
