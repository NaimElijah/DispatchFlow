package com.dispatchflow.archfixture.planning.domain;

/** DL §13: the planning domain calls the package-private constructor. */
public final class BuildsAReportInTheDomain {

    static ValidationReport build() {
        return new ValidationReport();
    }
}
