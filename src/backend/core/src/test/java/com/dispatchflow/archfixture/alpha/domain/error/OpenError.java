package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.shared.DomainError;

/** DL §3.8: an error interface that is not sealed lets errors escape its list. */
public interface OpenError extends DomainError {}
