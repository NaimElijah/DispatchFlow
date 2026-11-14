package com.dispatchflow.archfixture.planning.application;

import com.dispatchflow.archfixture.planning.domain.shaped.Evaluation;

/** DL §13: Planning's record-shaped Evaluation built with new outside the optimizer adapter. */
public final class BuildsAShapedEvaluation {

    static Evaluation build() {
        return new Evaluation(1);
    }
}
