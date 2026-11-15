package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: Class.forName loads a class from the classpath by name. */
public final class LoadsAClassByName {

    static Class<?> clock() throws ClassNotFoundException {
        return Class.forName("java.time.Clock");
    }
}
