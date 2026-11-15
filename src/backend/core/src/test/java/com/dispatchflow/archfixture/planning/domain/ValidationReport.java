package com.dispatchflow.archfixture.planning.domain;

/**
 * A ValidationReport whose constructor is package-private; accepted() shows a type may use its own
 * factory.
 */
public final class ValidationReport {

    ValidationReport() {}

    public static ValidationReport of() {
        return new ValidationReport();
    }

    public static ValidationReport accepted() {
        return of();
    }
}
