package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.web.WebReadsTheCurrentCaller;

/** Rule 20: {@code web.security} uses a class directly in {@code web}, which it forbids. */
public final class SessionUsesTheWeb {

    WebReadsTheCurrentCaller reader() {
        return new WebReadsTheCurrentCaller();
    }
}
