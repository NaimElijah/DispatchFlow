package com.dispatchflow.archfixture.zeta;

import com.dispatchflow.archfixture.zeta.peer.application.service.PeerUsageService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** Rule 15's other half: an annotation outside a module's root that imports a module's use case. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(PeerUsageService.class)
public @interface ImportsPeersUseCase {}
