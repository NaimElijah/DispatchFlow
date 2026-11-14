package com.dispatchflow.archfixture.planning.application;

import com.dispatchflow.archfixture.planning.domain.ValidationReport;

/** DL §13: a ValidationReport built outside the optimizer adapter. */
public final class BuildsAValidationReport {

    static ValidationReport build() {
        return ValidationReport.of();
    }
}
