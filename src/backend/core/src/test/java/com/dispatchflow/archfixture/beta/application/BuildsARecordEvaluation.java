package com.dispatchflow.archfixture.beta.application;

import com.dispatchflow.archfixture.beta.domain.Evaluation;

/** Allowed: another module's Evaluation is not Planning's optimizer result. */
public final class BuildsARecordEvaluation {

    static Evaluation build() {
        return new Evaluation(1);
    }
}
