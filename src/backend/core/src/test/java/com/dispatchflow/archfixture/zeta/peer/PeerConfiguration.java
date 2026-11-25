package com.dispatchflow.archfixture.zeta.peer;

import com.dispatchflow.archfixture.zeta.peer.application.service.PeerUsageService;
import org.springframework.context.annotation.Import;

/**
 * Rule 15: a module's configuration as it should be, package-private and importing only its own
 * module's service. It carries no {@code @Configuration}: the rule reads its name and modifiers, and an unannotated
 * class is never component-scanned, so it stays out of every context that scans the test tree.
 */
@Import(PeerUsageService.class)
class PeerConfiguration {}
