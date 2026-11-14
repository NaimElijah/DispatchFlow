package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * An {@code EventId} names its event by simple name, so no two events may share one across
 * modules (DL §7.3).
 */
class EventNameUniquenessTest {

    /** Every event simple name declared more than once, with the events that declare it. */
    private static SortedMap<String, List<String>> duplicatedNames(JavaClasses classes) {
        SortedMap<String, List<String>> duplicated = new TreeMap<>();
        classes.stream()
                .filter(type -> type.isAssignableTo(DomainEvent.class)
                        && !type.isInterface()
                        && !type.getModifiers().contains(JavaModifier.ABSTRACT))
                .collect(Collectors.groupingBy(
                        JavaClass::getSimpleName, Collectors.mapping(JavaClass::getName, Collectors.toList())))
                .forEach((name, events) -> {
                    if (events.size() > 1) {
                        duplicated.put(name, events.stream().sorted().toList());
                    }
                });
        return duplicated;
    }

    @Test
    void GivenTheProductionEvents_WhenTheirNamesAreCollected_ThenNoNameIsDeclaredTwice() {
        assertThat(duplicatedNames(ProductionClasses.ALL)).isEmpty();
    }

    @Test
    void GivenTwoModulesDeclaringOneEventName_WhenCollected_ThenTheNameIsReported() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.dispatchflow.archfixture");

        assertThat(duplicatedNames(fixtures))
                .containsOnlyKeys("Reported")
                .containsEntry(
                        "Reported",
                        List.of(
                                "com.dispatchflow.archfixture.alpha.events.Reported",
                                "com.dispatchflow.archfixture.beta.events.Reported"));
    }
}
