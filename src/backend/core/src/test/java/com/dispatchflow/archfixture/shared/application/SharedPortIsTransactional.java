package com.dispatchflow.archfixture.shared.application;

import org.springframework.transaction.annotation.Transactional;

/**
 * Rule 1: the kernel's application package falls under the kernel's purity rules, so it takes no
 * {@code @Transactional}.
 */
public final class SharedPortIsTransactional {

    @Transactional
    void run() {}
}
