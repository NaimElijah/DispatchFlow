package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.archfixture.beta.events.BetaHappened;

/** Rule 1: a domain must not depend on another module's events. */
public record DependsOnAnotherModulesEvents(BetaHappened event) {}
