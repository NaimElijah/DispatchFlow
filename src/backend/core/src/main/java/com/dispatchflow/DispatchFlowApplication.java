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

    /** How Spring Boot tells an option from a bare argument, which it ignores. */
    private static final String OPTION_PREFIX = "--";

    /**
     * Starts the core backend, or, when the first argument is {@value MigrateCommand#NAME}, applies
     * the pending migrations and returns ({@link MigrateCommand}).
     *
     * @param args command-line arguments, passed on to Spring Boot, the command's name left out
     * @throws IllegalArgumentException when an argument isn't a {@code --} option, but for a first
     *     {@value MigrateCommand#NAME}, or is {@code --migrate}: Spring Boot would ignore it, so a
     *     misplaced or misspelt command would start the whole core in the migrate step's place, holding
     *     the owner's credential, and never exit (ADR-146)
     */
    public static void main(String[] args) {
        if (args.length > 0 && MigrateCommand.NAME.equals(args[0])) {
            MigrateCommand.run(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (Arrays.stream(args).anyMatch(DispatchFlowApplication::isNoOptionOfTheCore)) {
            // The arguments stay out of the message: one of them may be a password.
            throw new IllegalArgumentException("The core takes only --options, and " + MigrateCommand.NAME
                    + " only as the first argument, spelt so");
        }
        SpringApplication.run(DispatchFlowApplication.class, args);
    }

    /** A bare word, which the core never reads, or the command written as an option. */
    private static boolean isNoOptionOfTheCore(String argument) {
        if (!argument.startsWith(OPTION_PREFIX)) {
            return true;
        }
        String name = argument.substring(OPTION_PREFIX.length()).split("=", 2)[0];
        return name.strip().equalsIgnoreCase(MigrateCommand.NAME);
    }
}
