package com.dispatchflow.archfixture.planning.application;

import com.dispatchflow.archfixture.planning.domain.Evaluation;

/** DL §13: an Evaluation built through its nested type's factory, outside the optimizer adapter. */
public final class BuildsThroughTheNestedBuilder {

    static Evaluation build() {
        return Evaluation.Builder.build();
    }
}
