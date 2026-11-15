package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.List;

/** Rule 4: a reference to String.toLowerCase() with no Locale reads the default locale when called. */
public final class LowerCasesWithTheDefaultLocaleByReference {

    static List<String> lowerCase(List<String> texts) {
        return texts.stream().map(String::toLowerCase).toList();
    }
}
