package com.dispatchflow.web.security;

import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Stands in for the process's environment variables in a test's context, so a variable a
 * developer exported, such as {@code DISPATCHFLOW_STANDIN_ACCOUNT} or {@code
 * DISPATCHFLOW_DEPLOYED}, changes no test. The source takes the environment's own name, so Spring
 * Boot maps its names as it maps real environment variables, and it replaces the real source.
 */
final class ProcessEnvironment {

    private ProcessEnvironment() {}

    /**
     * An initializer that makes the given variables the process's only ones.
     *
     * @param variables the environment variables, by their exported names
     * @return the initializer
     */
    static ApplicationContextInitializer<ConfigurableApplicationContext> holding(Map<String, String> variables) {
        return context -> context.getEnvironment()
                .getPropertySources()
                .addAfter(
                        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.copyOf(variables)));
    }

    /**
     * An initializer that leaves the process no environment variables.
     *
     * @return the initializer
     */
    static ApplicationContextInitializer<ConfigurableApplicationContext> empty() {
        return holding(Map.of());
    }
}
