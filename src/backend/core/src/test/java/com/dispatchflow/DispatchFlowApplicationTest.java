package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The jar's entry point, before any Spring context starts. Spring Boot ignores an argument that isn't
 * an option, so a misplaced or misspelt {@code migrate} would start the whole core in the migrate
 * step's place, holding the owner's credential, and it would never exit (ADR-146 decision 1).
 */
class DispatchFlowApplicationTest {

    private static final String PASSWORD = "not-a-real-password";
    private static final String OWNERS_PASSWORD = "--spring.datasource.password=" + PASSWORD;

    static Stream<Arguments> argumentsTheCoreWouldIgnore() {
        return Stream.of(
                Arguments.of((Object) new String[] {OWNERS_PASSWORD, MigrateCommand.NAME}),
                Arguments.of((Object) new String[] {"MIGRATE", OWNERS_PASSWORD}),
                Arguments.of((Object) new String[] {"migrate ", OWNERS_PASSWORD}),
                Arguments.of((Object) new String[] {"--migrate", OWNERS_PASSWORD}),
                Arguments.of((Object) new String[] {OWNERS_PASSWORD, "--spring.main.banner-mode", "off"}));
    }

    @ParameterizedTest
    @MethodSource("argumentsTheCoreWouldIgnore")
    void GivenAnArgumentTheCoreWouldIgnore_WhenTheJarStarts_ThenItRefusesWithoutNamingTheArguments(String[] arguments) {
        assertThatThrownBy(() -> DispatchFlowApplication.main(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first argument")
                .hasMessageNotContaining(PASSWORD);
    }
}
