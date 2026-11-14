package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.archfixture.alpha.domain.AlphaValue;

/** Rule 1: an events package must not depend on its domain. */
public record EventDependsOnItsDomain(AlphaValue value) {}
