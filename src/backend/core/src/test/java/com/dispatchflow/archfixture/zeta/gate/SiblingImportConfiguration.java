package com.dispatchflow.archfixture.zeta.gate;

import com.dispatchflow.archfixture.zeta.gate.application.serviceold.GateOldUseCase;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: a configuration that imports from {@code application.serviceold}, a sibling of its
 * module's {@code application.service}, not a subpackage of it. It carries no {@code
 * @Configuration}, so no context that scans the test tree reads it.
 */
@Import(GateOldUseCase.class)
class SiblingImportConfiguration {}
