package com.dispatchflow.archfixture.planning.domain;

/** A Evaluation only the optimizer adapter may build; accepted() shows a type may use its own factory. */
public final class Evaluation {

    private Evaluation() {}

    public static Evaluation of() {
        return new Evaluation();
    }

    public static Evaluation accepted() {
        return of();
    }
}
