package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.architecture.ProductionClasses;
import com.dispatchflow.shared.DomainError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class WebCodesTest {

    private static final String CODE_FIELD = "CODE";
    private static final Set<JavaModifier> CONSTANT =
            Set.of(JavaModifier.PUBLIC, JavaModifier.STATIC, JavaModifier.FINAL);

    /** Every code the modules and the kernel declare: each error's, and each exception's CODE. */
    private static Set<String> modulesCodes() {
        Stream<String> errors = ProductionClasses.ALL.stream()
                .filter(type -> type.isAssignableTo(DomainError.class))
                .filter(type -> !type.isInterface() && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .map(type -> DomainError.codeOf(type.reflect()));
        Stream<String> exceptions = ProductionClasses.ALL.stream()
                .flatMap(type -> type.tryGetField(CODE_FIELD).stream())
                .filter(field -> field.getModifiers().containsAll(CONSTANT))
                .filter(field -> field.getRawType().isEquivalentTo(String.class))
                .map(WebCodesTest::valueOf);
        return Stream.concat(errors, exceptions).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String valueOf(JavaField field) {
        try {
            return (String) field.reflect().get(null);
        } catch (IllegalAccessException unreadable) {
            throw new AssertionError("Can't read " + field.getFullName(), unreadable);
        }
    }

    @Test
    void GivenTheWebsOwnCodes_WhenComparedWithTheModulesCodes_ThenNoneIsAModulesCode() {
        Set<String> modules = modulesCodes();

        assertThat(modules).contains("NOT_FOUND", "STALE_VERSION", "STEP_UP_REQUIRED");
        assertThat(WebCodes.ALL).doesNotContainAnyElementsOf(modules);
    }

    @Test
    void GivenTheOverrideCodesOfTheStatusTable_WhenComparedWithTheModulesCodes_ThenEachIsDeclaredByAModule() {
        assertThat(modulesCodes()).containsAll(KernelErrorHandler.OVERRIDES.keySet());
    }

    @Test
    void GivenTheCodesAnsweredAsARefusedToken_WhenComparedWithTheModulesCodes_ThenEachIsDeclaredByAModule() {
        assertThat(modulesCodes()).containsAll(KernelErrorHandler.ANSWERED_AS_A_REFUSED_TOKEN);
    }

    @Test
    void GivenTheProductionClasses_WhenTheirCodeConstantsAreRead_ThenTheKernelsExceptionsAreAmongThem() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type -> type.tryGetField(CODE_FIELD).isPresent())
                        .map(JavaClass::getSimpleName))
                .contains("NotFound", "StaleVersion");
    }
}
