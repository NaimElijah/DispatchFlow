package com.dispatchflow.archfixture.beta.application;

import com.dispatchflow.archfixture.beta.domain.Evaluation;

/** DL §13: a record Evaluation built with new outside the optimizer adapter. */
public final class BuildsARecordEvaluation {

    static Evaluation build() {
        return new Evaluation(1);
    }
}
