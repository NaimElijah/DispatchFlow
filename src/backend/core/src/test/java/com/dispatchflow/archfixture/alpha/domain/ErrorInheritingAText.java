package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: free text inherited from a supertype. */
public final class ErrorInheritingAText extends AbstractReasonedError implements DomainError {

    public ErrorInheritingAText(String reason) {
        super(reason);
    }
}
