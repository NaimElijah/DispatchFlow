package com.dispatchflow.archfixture.planning.domain;

/** A ValidationReport only the optimizer adapter may build; accepted() shows a type may use its own factory. */
public final class ValidationReport {

    private ValidationReport() {}

    public static ValidationReport of() {
        return new ValidationReport();
    }

    public static ValidationReport accepted() {
        return of();
    }
}
