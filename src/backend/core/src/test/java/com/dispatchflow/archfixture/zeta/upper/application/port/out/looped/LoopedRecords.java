package com.dispatchflow.archfixture.zeta.upper.application.port.out.looped;

import com.dispatchflow.archfixture.zeta.upper.application.service.looped.LoopedUseCase;

/** Rule 7: a port that names the use case calling it, which closes a cycle. */
public interface LoopedRecords {

    /** Appends what a use case recorded. */
    void append(LoopedUseCase from);
}
