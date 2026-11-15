package com.dispatchflow.archfixture.zeta.upper.application.service.looped;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.looped.LoopedRecords;

/** Rule 7: a use case whose port names it back, so two application packages form a cycle. */
public record LoopedUseCase(LoopedRecords records) {}
