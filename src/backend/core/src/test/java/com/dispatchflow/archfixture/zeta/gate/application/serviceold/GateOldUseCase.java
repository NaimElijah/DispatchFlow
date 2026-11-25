package com.dispatchflow.archfixture.zeta.gate.application.serviceold;

/**
 * Rule 15: a class in a sibling of {@code gate}'s {@code application.service} whose name only
 * starts the same, which no configuration may import.
 */
public record GateOldUseCase(int count) {}
