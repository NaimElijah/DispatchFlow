package com.dispatchflow.archfixture.alpha.domain.io.classpath;

import java.util.ServiceLoader;

/** Rule 4: a ServiceLoader finds its providers on the classpath. */
public final class LoadsAService {

    static boolean hasAProvider() {
        return ServiceLoader.load(Runnable.class).findFirst().isPresent();
    }
}
