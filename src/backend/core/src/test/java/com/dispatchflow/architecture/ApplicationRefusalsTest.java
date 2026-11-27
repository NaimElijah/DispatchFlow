package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.refusal.adapter.CodedAdapterError;
import com.dispatchflow.archfixture.refusal.application.CodedByAConstant;
import com.dispatchflow.archfixture.refusal.application.MarkedRefusal;
import com.dispatchflow.archfixture.refusal.application.UnmarkedRefusal;
import com.dispatchflow.shared.application.ApplicationRefusal;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    /**
     * The exceptions in an {@code application} package, at any depth, that carry a code, through a
     * {@code code()} method or a {@code CODE} constant, and don't implement {@link
     * ApplicationRefusal}.
     */
    static List<String> unmarkedCodedExceptionsOfTheApplicationLayer(JavaClasses classes) {
        return classes.stream()
                .filter(type ->
                        Arrays.asList(type.getPackageName().split("\\.")).contains("application"))
                .filter(type -> type.isAssignableTo(RuntimeException.class))
                .filter(ApplicationRefusalsTest::carriesACode)
                .filter(type -> !type.isAssignableTo(ApplicationRefusal.class))
                .map(JavaClass::getName)
                .toList();
    }

    private static boolean carriesACode(JavaClass type) {
        boolean method = type.getAllMethods().stream()
                .anyMatch(each -> each.getName().equals("code")
                        && each.getRawParameterTypes().isEmpty());
        boolean constant = type.getFields().stream()
                .anyMatch(field ->
                        field.getName().equals("CODE") && field.getModifiers().contains(JavaModifier.STATIC));
        return method || constant;
    }

    @Test
    void GivenEveryCodedExceptionOfTheApplicationLayer_WhenListed_ThenEachIsARefusalOrNamedAsNone() {
        assertThat(unmarkedCodedExceptionsOfTheApplicationLayer(ProductionClasses.ALL))
                .containsExactlyInAnyOrderElementsOf(NO_REFUSAL.keySet());
    }

    @Test
    void GivenCodedExceptionsInAndOutOfTheApplicationLayer_WhenChecked_ThenOnlyTheUnmarkedApplicationOnesAreListed() {
        JavaClasses fixtures = FixtureClasses.only(
                UnmarkedRefusal.class, CodedByAConstant.class, MarkedRefusal.class, CodedAdapterError.class);

        assertThat(unmarkedCodedExceptionsOfTheApplicationLayer(fixtures))
                .containsExactlyInAnyOrder(UnmarkedRefusal.class.getName(), CodedByAConstant.class.getName());
    }
}
