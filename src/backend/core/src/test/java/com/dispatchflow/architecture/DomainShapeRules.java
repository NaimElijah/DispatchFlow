package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.IsraelDateTime;
import com.dispatchflow.shared.JobId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rules on the shape of domain types, which the purity rules can't see: who may construct a
 * restricted type (DL §13, §3.5), what an event may carry (DL §7.3), and what an error may carry
 * (DL §3.8, ADR-043). Each matches by type or name across the whole root, so a module added
 * later is covered without editing a rule.
 */
final class DomainShapeRules {

    private static final String EVENTS = "events";
    private static final String CODE = "code";

    /**
     * The kernel types an event may carry: the typed IDs, the shared values other than {@link
     * PhoneNumber} and {@link GeoPoint}, {@link EventId} and {@link Version}. Never a building
     * block such as {@code FreshIds} or {@code RuleViolation}.
     */
    private static final Set<String> EVENT_KERNEL_TYPES = Stream.of(
                    AccountId.class,
                    SessionId.class,
                    DepotId.class,
                    VehicleId.class,
                    CustomerId.class,
                    OrderId.class,
                    ImportBatchId.class,
                    PlanId.class,
                    RouteId.class,
                    StopId.class,
                    JobId.class,
                    ProposalId.class,
                    AlertId.class,
                    RatingRequestId.class,
                    DeliveryDate.class,
                    IsraelDateTime.class,
                    DaySeconds.class,
                    TimeWindow.class,
                    Weight.class,
                    Volume.class,
                    Priority.class,
                    EventId.class,
                    Version.class)
            .map(Class::getName)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> EVENT_NUMBERS_AND_FLAGS =
            Set.of("int", "long", "boolean", "java.lang.Integer", "java.lang.Long", "java.lang.Boolean");

    private static final Set<String> PERSONAL_KERNEL_VALUES =
            Set.of(PhoneNumber.class.getName(), GeoPoint.class.getName());

    /** DL §3.3's personal values that later modules declare, matched by simple name. */
    private static final Set<String> PERSONAL_MODULE_VALUES = Set.of("EmailAddress", "Address", "NormalizedAddress");

    // An enum's own name and ordinal fields are not parameters the enum's code chose.
    private static final String ENUM = "java.lang.Enum";

    private DomainShapeRules() {}

    /**
     * DL §3.5 and §13: aggregates, lock-read snapshots and Planning's optimizer results, with every
     * type nested in an optimizer result, are built only by their factories, so their constructors
     * are private. So is {@code ValidatedSolution}'s, whose factory anyone may call. A record can't
     * qualify: its canonical constructor is as visible as the record.
     */
    static ArchRule restrictedConstructorsArePrivate(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("declare only private constructors when restricted") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isRestricted(item)) {
                            return;
                        }
                        item.getConstructors().stream()
                                .filter(constructor ->
                                        !constructor.getModifiers().contains(JavaModifier.PRIVATE))
                                .forEach(constructor -> events.add(SimpleConditionEvent.violated(
                                        constructor, constructor.getFullName() + " is not private")));
                    }
                })
                .because("DL §3.5 and §13: only a restricted type's own factory may build it");
    }

    /** DL §7.3: an event is published language, so it lives in its module's events package. */
    static ArchRule eventsLiveInEventsPackages(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("live in an events package when a DomainEvent") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (isConcrete(item, DomainEvent.class) && !isInEventsPackage(root, item)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is a DomainEvent outside an events package"));
                        }
                    }
                })
                .because("DL §7.3: events are a module's published language, in <module>.events");
    }

    /**
     * DL §7.3: an event carries the kernel types in {@link #EVENT_KERNEL_TYPES}, enums of its own
     * events package, {@code Instant}, {@code int}, {@code long}, {@code boolean} and their boxes,
     * and lists of these; never an array, free text or personal data. Fields inherited from a
     * supertype count too.
     */
    static ArchRule eventFieldsAreClosed(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("carry only the field types DL §7.3 allows when a DomainEvent") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isConcrete(item, DomainEvent.class)) {
                            return;
                        }
                        instanceFields(item)
                                .filter(field -> !isAllowedEventType(field.getType(), item, true))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field, field.getFullName() + " has a type an event may not carry")));
                    }
                })
                .because("DL §7.3: an event holds IDs, kernel values and its own enums, never free text or"
                        + " personal data");
    }

    /**
     * ADR-043 and NFR-19: an error's fields become the response's {@code params}, so none is text
     * (any {@link CharSequence}), an {@code Object}, an array, or a personal value, at any depth of
     * its type arguments; fields inherited from a supertype count too. A value type that wraps a
     * String is not looked into: its own type decides what it may hold.
     */
    static ArchRule errorParametersCarryNoPersonalData(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(
                        new ArchCondition<JavaClass>(
                                "carry no text, Object, array or personal value when a DomainError") {
                            @Override
                            public void check(JavaClass item, ConditionEvents events) {
                                if (!isConcrete(item, DomainError.class)) {
                                    return;
                                }
                                instanceFields(item)
                                        .filter(field -> erasuresIn(field.getType())
                                                .anyMatch(DomainShapeRules::isForbiddenParameter))
                                        .forEach(field -> events.add(SimpleConditionEvent.violated(
                                                field,
                                                field.getFullName() + " could carry personal data into params")));
                            }
                        })
                .because("ADR-043: params carry no personal data, so an error field is never free text");
    }

    /** DL §3.8: every error interface is sealed, so its permitted errors are the complete list. */
    static ArchRule errorInterfacesAreSealed(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("be sealed when an interface extending DomainError") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (item.isInterface()
                                && item.isAssignableTo(DomainError.class)
                                && !item.isEquivalentTo(DomainError.class)
                                && !item.reflect().isSealed()) {
                            events.add(SimpleConditionEvent.violated(item, item.getName() + " is not sealed"));
                        }
                    }
                })
                .because("DL §3.8: one sealed error type per module, so the code list and the tests see every"
                        + " error");
    }

    /**
     * DL §3.8: a code is derived from the type's name by one function, so no type declares its own.
     */
    static ArchRule errorCodesAreDerived(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("not declare code() when a DomainError") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.isAssignableTo(DomainError.class) || item.isEquivalentTo(DomainError.class)) {
                            return;
                        }
                        item.getMethods().stream()
                                .filter(method -> method.getName().equals(CODE)
                                        && method.getRawParameterTypes().isEmpty())
                                .forEach(method -> events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + " overrides the derived code")));
                    }
                })
                .because("DL §3.8: each code is derived from its type's simple name, which the uniqueness test"
                        + " checks");
    }

    private static boolean isRestricted(JavaClass type) {
        boolean aggregate = type.isAssignableTo(AggregateRoot.class) && !type.isEquivalentTo(AggregateRoot.class);
        boolean readUnderLock = type.getMethods().stream()
                .anyMatch(method -> method.getName().equals(RestrictedTypes.READ_UNDER_LOCK)
                        && method.getModifiers().contains(JavaModifier.STATIC));
        return aggregate
                || readUnderLock
                || RestrictedTypes.optimizerResultOf(type).isPresent()
                || RestrictedTypes.isValidatedSolution(type);
    }

    private static boolean isConcrete(JavaClass type, Class<?> supertype) {
        return type.isAssignableTo(supertype)
                && !type.isInterface()
                && !type.getModifiers().contains(JavaModifier.ABSTRACT);
    }

    private static boolean isInEventsPackage(String root, JavaClass type) {
        String prefix = root + ".";
        if (!type.getPackageName().startsWith(prefix)) {
            return false;
        }
        String[] segments = type.getPackageName().substring(prefix.length()).split("\\.");
        return segments.length > 1 && segments[1].equals(EVENTS);
    }

    /** The instance fields a type declares or inherits. */
    private static Stream<JavaField> instanceFields(JavaClass type) {
        return type.getAllFields().stream()
                .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> !field.getOwner().getName().equals(ENUM));
    }

    private static boolean isAllowedEventType(JavaType type, JavaClass event, boolean listAllowed) {
        JavaClass raw = type.toErasure();
        if (raw.isArray()) {
            return false;
        }
        if (raw.isEquivalentTo(List.class)) {
            return listAllowed
                    && type instanceof JavaParameterizedType parameterized
                    && parameterized.getActualTypeArguments().size() == 1
                    && isAllowedEventType(parameterized.getActualTypeArguments().get(0), event, false);
        }
        return EVENT_NUMBERS_AND_FLAGS.contains(raw.getName())
                || raw.isEquivalentTo(Instant.class)
                || (raw.isEnum() && raw.getPackageName().equals(event.getPackageName()))
                || EVENT_KERNEL_TYPES.contains(raw.getName());
    }

    private static boolean isForbiddenParameter(JavaClass type) {
        return type.isArray()
                || type.isEquivalentTo(Object.class)
                || type.isAssignableTo(CharSequence.class)
                || PERSONAL_KERNEL_VALUES.contains(type.getName())
                || PERSONAL_MODULE_VALUES.contains(type.getSimpleName());
    }

    /** The erasure of a type and of every type argument inside it, at any depth. */
    private static Stream<JavaClass> erasuresIn(JavaType type) {
        Stream<JavaClass> self = Stream.of(type.toErasure());
        if (type instanceof JavaParameterizedType parameterized) {
            return Stream.concat(
                    self, parameterized.getActualTypeArguments().stream().flatMap(DomainShapeRules::erasuresIn));
        }
        return self;
    }
}
