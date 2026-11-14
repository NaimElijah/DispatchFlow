package com.dispatchflow.archfixture.planning.domain;

/** A ValidatedSolution shaped as a record, so anyone may skip of(ValidationReport). */
public record ValidatedSolution(ValidationReport report) {

    public static ValidatedSolution of(ValidationReport report) {
        return new ValidatedSolution(report);
    }
}
