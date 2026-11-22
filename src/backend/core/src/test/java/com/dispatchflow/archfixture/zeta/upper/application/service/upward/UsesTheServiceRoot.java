package com.dispatchflow.archfixture.zeta.upper.application.service.upward;

import com.dispatchflow.archfixture.zeta.upper.application.service.UpperUseCase;

/**
 * Rule 7's allowed direction: a class of a subpackage that uses the {@code service} root above it,
 * which uses nothing of the subpackage. The rule must not flag it.
 */
public record UsesTheServiceRoot(UpperUseCase above) {}
