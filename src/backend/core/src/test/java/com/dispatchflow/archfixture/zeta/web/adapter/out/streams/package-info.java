/**
 * An adapter of web, which is no module, so rule 10 lets any adapter use it. Rule 2 still refuses
 * every module a dependency on web, and rule 11 every shared adapter (ADR-131, ADR-140).
 */
@NullMarked
package com.dispatchflow.archfixture.zeta.web.adapter.out.streams;

import org.jspecify.annotations.NullMarked;
