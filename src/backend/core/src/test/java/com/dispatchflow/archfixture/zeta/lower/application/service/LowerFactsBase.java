package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;

/** Rule 4: an abstract class is no implementation of its own, so it is not counted. */
public abstract class LowerFactsBase implements LowerFacts {}
