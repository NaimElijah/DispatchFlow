package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.upper.application.service.used.UsedByTheServiceRoot;

/**
 * Rule 7: a class of the {@code service} root that uses a subpackage which never names the root.
 * The dependency points one way, so the cycle rule allows it; the root rule must not.
 */
public record ServiceRootUsingASubpackage(UsedByTheServiceRoot below) {}
