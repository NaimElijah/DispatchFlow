package com.dispatchflow.archfixture.epsilon.domain;

import com.dispatchflow.archfixture.epsilon.domain.used.UsedByTheRoot;

/**
 * ADR-118 rule 2: a domain root type that uses a subpackage which never names the root. The
 * dependency points one way, so the cycle rule allows it; the root rule must not.
 */
public record RootUsingASubpackage(UsedByTheRoot below) {}
