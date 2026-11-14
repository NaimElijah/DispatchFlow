package com.dispatchflow.archfixture.alpha.domain;

/** Allowed by rule 5: catching a generic exception, without creating one. */
public final class CatchesAnIllegalArgument {

    static int parsed(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
