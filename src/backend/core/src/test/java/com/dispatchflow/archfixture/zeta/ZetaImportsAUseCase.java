package com.dispatchflow.archfixture.zeta;

import com.dispatchflow.archfixture.zeta.peer.application.service.PeerUsageService;
import org.springframework.context.annotation.Import;

/** Rule 15's other half: a class outside a module's root that imports a module's use case. */
@Import(PeerUsageService.class)
final class ZetaImportsAUseCase {}
