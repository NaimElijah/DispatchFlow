package com.dispatchflow.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * The whole core, under the {@code test} profile, holds the stand-in session and the guard that
 * refuses it elsewhere, so neither can drop out of the scan unseen: {@code StandInGuardTest} names
 * both classes itself, and would pass without them (ADR-140 decision 4, ADR-147).
 */
@IntegrationTest
class StandInIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void GivenTheTestProfile_WhenTheCoreStarts_ThenTheStandInItsGuardAndTheAccessInterceptorAreBeans() {
        assertThat(context.getBeanNamesForType(StandInGuard.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(StandInSession.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AccessInterceptor.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(AccessInterceptorRegistration.class))
                .hasSize(1);
        assertThat(context.getBeanNamesForType(NotSignedInHandler.class)).hasSize(1);
    }
}
