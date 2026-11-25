package com.dispatchflow.archfixture.zeta;

import com.dispatchflow.archfixture.zeta.lower.application.api.LowerFacts;
import com.dispatchflow.archfixture.zeta.lower.application.service.LowerFactsService;
import org.springframework.context.annotation.Bean;

/**
 * Rule 15's other half: a class outside a module's root whose {@code @Bean} method makes a module's
 * use case, behind its api's type.
 */
final class ZetaMakesAUseCase {

    @Bean
    LowerFacts lowerFacts() {
        return new LowerFactsService();
    }
}
