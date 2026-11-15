package com.dispatchflow.archfixture.zeta.lower.application.spi;

/** Rule 1: a record beside the spi interfaces, open to every module that implements one of them. */
public record UsageQuestion(int thing) {}
