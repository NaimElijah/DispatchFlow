package com.dispatchflow.archfixture.zeta.lower.application.api;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;
import java.util.List;

/** Rule 3: an api record must not hold a domain type, even as a list's element. */
public record LowerFactsLeakThroughAList(List<LowerValue> values) {}
