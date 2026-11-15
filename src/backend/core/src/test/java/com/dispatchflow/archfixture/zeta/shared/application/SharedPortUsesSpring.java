package com.dispatchflow.archfixture.zeta.shared.application;

import org.springframework.util.StringUtils;

/** Rule 1: the shared application package must not depend on Spring. */
public final class SharedPortUsesSpring {

    static boolean blank(String text) {
        return !StringUtils.hasText(text);
    }
}
