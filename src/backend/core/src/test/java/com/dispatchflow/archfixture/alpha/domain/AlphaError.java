package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** Errors whose codes another fixture module repeats. */
public sealed interface AlphaError extends DomainError {

    record DateFull() implements AlphaError {}

    record DateInPast() implements AlphaError {}
}
