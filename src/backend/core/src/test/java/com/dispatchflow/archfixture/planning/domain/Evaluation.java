package com.dispatchflow.archfixture.planning.domain;

/** An Evaluation only the optimizer adapter may build; its own code and nested types may. */
public final class Evaluation {

    private Evaluation() {}

    public static Evaluation of() {
        return new Evaluation();
    }

    public static Evaluation accepted() {
        return of();
    }

    /** A nested type builds through its enclosing type's factory and constructor. */
    public static final class Builder {

        private Builder() {}

        public static Evaluation build() {
            return Evaluation.of();
        }

        public static Evaluation construct() {
            return new Evaluation();
        }
    }
}
