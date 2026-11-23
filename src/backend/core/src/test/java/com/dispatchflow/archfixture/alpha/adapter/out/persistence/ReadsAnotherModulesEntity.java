package com.dispatchflow.archfixture.alpha.adapter.out.persistence;

import com.dispatchflow.archfixture.beta.domain.BetaLine;
import java.util.List;

/**
 * Rule: a module's persistence adapter uses only its own module's entities (ADR-041).
 *
 * @param lines another module's entities, which it must not read
 */
public record ReadsAnotherModulesEntity(List<BetaLine> lines) {}
