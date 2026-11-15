package com.dispatchflow.archfixture.zeta.lower.application.api;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/** Rule 4: a sealed interface models a fact's variants, so it is not a port and is skipped. */
public sealed interface LowerOutcome {

    /** The outcome when the fact was taken. */
    record Taken(ZetaKernelValue value) implements LowerOutcome {}

    /** The outcome when it was refused. */
    record Refused(String code) implements LowerOutcome {}
}
