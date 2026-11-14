package com.dispatchflow.archfixture.planning.application;

import com.dispatchflow.archfixture.planning.domain.ValidatedSolution;
import com.dispatchflow.archfixture.planning.domain.ValidationReport;

/** Allowed by DL §13: any caller may use ValidatedSolution.of. */
public final class WrapsAValidationReport {

    static ValidatedSolution wrap(ValidationReport report) {
        return ValidatedSolution.of(report);
    }
}
