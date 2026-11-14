package com.dispatchflow.archfixture.alpha.events;

import com.dispatchflow.archfixture.beta.events.BetaHappened;

/** Rule 1: events must not depend on another module's events. */
public record EventDependsOnAnotherModulesEvents(BetaHappened event) {}
