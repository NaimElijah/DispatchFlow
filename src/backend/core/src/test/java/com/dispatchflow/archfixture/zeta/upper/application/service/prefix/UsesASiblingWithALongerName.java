package com.dispatchflow.archfixture.zeta.upper.application.service.prefix;

import com.dispatchflow.archfixture.zeta.upper.application.service.prefixed.SiblingWithALongerName;

/**
 * Rule 7's boundary: a class that uses a sibling package whose name starts with its own package's
 * name, as {@code service.plan} would use {@code service.planning}. The sibling is not beneath it, so
 * the rule must not flag it.
 */
public record UsesASiblingWithALongerName(SiblingWithALongerName sibling) {}
