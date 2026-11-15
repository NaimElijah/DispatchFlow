package com.dispatchflow.archfixture.alpha.domain;

import java.util.Locale;
import java.util.ResourceBundle;

/** Rule 4: a ResourceBundle loads its text from the classpath. */
public final class LoadsAResourceBundle {

    static String greeting() {
        return ResourceBundle.getBundle("messages", Locale.ROOT).getString("greeting");
    }
}
