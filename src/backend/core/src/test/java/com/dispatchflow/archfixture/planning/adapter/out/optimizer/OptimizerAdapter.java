package com.dispatchflow.archfixture.planning.adapter.out.optimizer;

import com.dispatchflow.archfixture.planning.domain.Evaluation;
import com.dispatchflow.archfixture.planning.domain.ValidationReport;

/** Allowed: the optimizer adapter builds both. */
public final class OptimizerAdapter {

    static ValidationReport report() {
        return ValidationReport.of();
    }

    static Evaluation evaluation() {
        return Evaluation.of();
    }
}
