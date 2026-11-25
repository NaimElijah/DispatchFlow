package com.dispatchflow.archfixture.zeta.gate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Rule 15: a configuration that writes a constructor call twice, in a {@code @Bean} method. */
@Import({})
class GateConfiguration {

    @Bean
    Object gateMarker() {
        return new Object();
    }
}
