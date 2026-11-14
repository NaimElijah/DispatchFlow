package com.dispatchflow.archfixture.alpha.domain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Rule 4: java.nio.file. */
public final class ReadsAPath {

    static boolean exists(Path path) {
        return Files.exists(path);
    }
}
