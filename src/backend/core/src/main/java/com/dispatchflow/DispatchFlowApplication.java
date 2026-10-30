package com.dispatchflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the DispatchFlow core backend.
 *
 * <p>Component scanning starts here, so every bounded context lives in a sub-package of
 * {@code com.dispatchflow}.
 */
@SpringBootApplication
public class DispatchFlowApplication {

    /**
     * Starts the core backend.
     *
     * @param args command-line arguments, passed on to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(DispatchFlowApplication.class, args);
    }
}
