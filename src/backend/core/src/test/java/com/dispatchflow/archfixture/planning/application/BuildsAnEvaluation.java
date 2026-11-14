package com.dispatchflow.archfixture.planning.application;

import com.dispatchflow.archfixture.planning.domain.Evaluation;
import java.util.function.Supplier;

/** DL §13: an Evaluation built, by reference, outside the optimizer adapter. */
public final class BuildsAnEvaluation {

    static Supplier<Evaluation> builder() {
        return Evaluation::of;
    }
}
