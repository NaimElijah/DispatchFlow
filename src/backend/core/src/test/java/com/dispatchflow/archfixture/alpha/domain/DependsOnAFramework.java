package com.dispatchflow.archfixture.alpha.domain;

import org.springframework.util.StringUtils;

/** Rule 1: a domain must not depend on a framework. */
public final class DependsOnAFramework {

    static boolean blank(String text) {
        return !StringUtils.hasText(text);
    }
}
