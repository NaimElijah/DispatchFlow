package com.dispatchflow.architecture;

import com.dispatchflow.shared.AggregateRoot;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.PackageMatcher;
import java.util.Optional;
import java.util.Set;

/**
 * The types only one caller may build, named once for {@link DomainPurityRules} and {@link
 * DomainShapeRules}: aggregates, which only their factories create and only Hibernate rebuilds,
 * snapshots read by {@code readUnderLock} (DL §3.5, §5), and Planning's optimizer results (DL §13). The optimizer results are matched by
 * name in Planning's domain only, so another module's type, or a generated client model, that
 * shares a name is not caught.
 */
final class RestrictedTypes {

    static final String READ_UNDER_LOCK = "readUnderLock";

    private static final Set<String> OPTIMIZER_RESULTS = Set.of("ValidationReport", "Evaluation");
    private static final String VALIDATED_SOLUTION = "ValidatedSolution";
    private static final PackageMatcher PLANNING_DOMAIN = PackageMatcher.of("..planning.domain..");

    private RestrictedTypes() {}

    /** Whether a type is an aggregate root, which only Hibernate rebuilds (ADR-126). */
    static boolean isAggregate(JavaClass type) {
        return type.isAssignableTo(AggregateRoot.class) && !type.isEquivalentTo(AggregateRoot.class);
    }

    /**
     * Whether a type is a snapshot read under a lock: it declares a static {@code readUnderLock}.
     */
    static boolean readsUnderLock(JavaClass type) {
        return type.getMethods().stream()
                .anyMatch(method -> method.getName().equals(READ_UNDER_LOCK)
                        && method.getModifiers().contains(JavaModifier.STATIC));
    }

    /**
     * The optimizer result a type is, or is nested in at any depth, so a nested type's factory is
     * as restricted as its enclosing type's.
     */
    static Optional<JavaClass> optimizerResultOf(JavaClass type) {
        Optional<JavaClass> candidate = Optional.of(type);
        while (candidate.isPresent()) {
            JavaClass current = candidate.get();
            if (OPTIMIZER_RESULTS.contains(current.getSimpleName()) && isInPlanningDomain(current)) {
                return candidate;
            }
            candidate = current.getEnclosingClass();
        }
        return Optional.empty();
    }

    /**
     * Whether a type is Planning's {@code ValidatedSolution}. Its constructor is private, but any
     * caller may use its factory, {@code of(ValidationReport)} (DL §13).
     */
    static boolean isValidatedSolution(JavaClass type) {
        return type.getSimpleName().equals(VALIDATED_SOLUTION) && isInPlanningDomain(type);
    }

    /** Whether one type is another, or is nested in it at any depth. */
    static boolean isWithin(JavaClass type, JavaClass outer) {
        Optional<JavaClass> candidate = Optional.of(type);
        while (candidate.isPresent()) {
            if (candidate.get().equals(outer)) {
                return true;
            }
            candidate = candidate.get().getEnclosingClass();
        }
        return false;
    }

    private static boolean isInPlanningDomain(JavaClass type) {
        return PLANNING_DOMAIN.matches(type.getPackageName());
    }
}
