package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.application.ApplicationRefusal;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Every exception with a code that the application layer raises is an {@link ApplicationRefusal},
 * which Audit's {@code Refusals} records, or is named here as no attempt's refusal, with why
 * (ADR-150 decision 1). So a new refusal that a module declares beside its use cases can't pass
 * the helper unrecorded.
 */
class ApplicationRefusalsTest {

    /** The coded exceptions of the application layer that are no attempt's refusal, and why. */
    private static final Map<String, String> NO_REFUSAL = Map.of(
            "com.dispatchflow.shared.application.StaleVersion",
            "a race, which the call wrapper records once it answers it",
            "com.dispatchflow.access.application.service.session.SignInRefused",
            "the substitute sign-in answers once the specific refusal is recorded",
            "com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest",
            "a defect of the request the core built, which fails loudly");

    private static final Predicate<JavaClass> CODED_EXCEPTION_OF_THE_APPLICATION_LAYER =
            type -> type.getPackageName().contains(".application")
                    && type.isAssignableTo(RuntimeException.class)
                    && type.getAllMethods().stream()
                            .anyMatch(method -> method.getName().equals("code")
                                    && method.getRawParameterTypes().isEmpty());

    @Test
    void GivenEveryCodedExceptionOfTheApplicationLayer_WhenListed_ThenEachIsARefusalOrNamedAsNone() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(CODED_EXCEPTION_OF_THE_APPLICATION_LAYER)
                        .filter(type -> !type.isAssignableTo(ApplicationRefusal.class))
                        .map(JavaClass::getName))
                .containsExactlyInAnyOrderElementsOf(NO_REFUSAL.keySet());
    }

    @Test
    void GivenTheNamedNonRefusals_WhenLookedUp_ThenEachIsACodedExceptionOfTheApplicationLayer() {
        assertThat(NO_REFUSAL.keySet())
                .allSatisfy(name -> assertThat(ProductionClasses.ALL.get(name))
                        .matches(
                                CODED_EXCEPTION_OF_THE_APPLICATION_LAYER,
                                "a coded exception of the application layer"));
    }
}
