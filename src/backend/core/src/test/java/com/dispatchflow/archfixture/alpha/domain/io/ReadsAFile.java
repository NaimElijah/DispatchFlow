package com.dispatchflow.archfixture.alpha.domain.io;

import java.io.File;

/** Rule 4: java.io. */
public final class ReadsAFile {

    static boolean exists(String name) {
        return new File(name).exists();
    }
}
