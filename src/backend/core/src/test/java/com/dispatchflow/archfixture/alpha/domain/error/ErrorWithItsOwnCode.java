package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.shared.DomainError;

/** DL §3.8: a code is always derived from the type name. */
public record ErrorWithItsOwnCode(int count) implements DomainError {

    @Override
    public String code() {
        return "SOMETHING_ELSE";
    }
}
