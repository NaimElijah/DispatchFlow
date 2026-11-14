package com.dispatchflow.archfixture.planning.domain;

/** An Evaluation only the optimizer adapter may build; it and its nested types may use each other. */
public final class Evaluation {

    private Evaluation() {}

    public static Evaluation of() {
        return new Evaluation();
    }

    public static Evaluation accepted() {
        return of();
    }

    static Evaluation throughTheBuilder() {
        return Builder.build();
    }

    /** A nested type with a visible constructor and a public factory: both are holes. */
    public static final class Builder {

        public Builder() {}

        public static Evaluation build() {
            return Evaluation.of();
        }

        public static Evaluation construct() {
            return new Evaluation();
        }
    }
}
