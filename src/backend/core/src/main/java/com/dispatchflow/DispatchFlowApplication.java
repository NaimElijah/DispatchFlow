package com.dispatchflow;

import java.util.Arrays;
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
     * Starts the core backend, or, when the first argument is {@value MigrateCommand#NAME}, applies
     * the pending migrations and returns ({@link MigrateCommand}).
     *
     * @param args command-line arguments, passed on to Spring Boot, the command's name left out
     */
    public static void main(String[] args) {
        if (args.length > 0 && MigrateCommand.NAME.equals(args[0])) {
            MigrateCommand.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(DispatchFlowApplication.class, args);
    }
}
