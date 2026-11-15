package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.dispatchflow.shared.AccountId;
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
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlainText;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rules on the shape of domain types, which the purity rules can't see: who may construct a
 * restricted type (DL §13, §3.5), what an event may carry (DL §7.3), what an error may carry
 * (DL §3.8, ADR-043), and how a personal value prints (ADR-106). Each matches by type or name
 * across the whole root, so a module added later is covered without editing a rule.
 */
final class DomainShapeRules {

    private static final String EVENTS = "events";
    private static final String CODE = "code";
    private static final String TO_STRING = "toString";

    /** The types a rule looks inside: DispatchFlow's own, never the JDK's or a library's. */
    private static final String OWN_PACKAGES = "com.dispatchflow";

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

    private static final Set<String> EVENT_NUMBERS_AND_FLAGS = Set.of("int", "long", "boolean");

    /** A list can't hold a primitive, so a list of numbers or flags holds their boxes. */
    private static final Set<String> EVENT_LIST_BOXES =
            Set.of("java.lang.Integer", "java.lang.Long", "java.lang.Boolean");

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
                        if (isEvent(item) && !isInEventsPackage(root, item)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is a DomainEvent outside an events package"));
                        }
                    }
                })
                .because("DL §7.3: events are a module's published language, in <module>.events");
    }

    /**
     * DL §7.3: an event carries the kernel types in {@link #EVENT_KERNEL_TYPES}, enums of its own
     * events package, {@code Instant}, {@code int}, {@code long} and {@code boolean}, and lists of
     * these, whose numbers and flags are boxed; never a boxed field, which could be null, an array,
     * free text or personal data. A type marked {@link
     * PersonalData} is refused even where the list would allow it (ADR-106). Fields inherited from a
     * supertype count too.
     */
    static ArchRule eventFieldsAreClosed(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("carry only the field types DL §7.3 allows when a DomainEvent") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isEvent(item)) {
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
     * (any {@link CharSequence}, or the kernel's {@link PlainText}), an {@code Object}, an array, or
     * a value marked {@link PersonalData} (ADR-106), at any depth of its type arguments; fields
     * inherited from a supertype count too. A DispatchFlow type is looked into, at any depth, for a
     * field that holds a marked value, so an unmarked wrapper can't hide one (ADR-106). Inside it,
     * a String is allowed: its own type decides what it may hold.
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
                                                .anyMatch(type -> isForbiddenParameter(type)
                                                        || holdsPersonalData(type, new HashSet<>())))
                                        .forEach(field -> events.add(SimpleConditionEvent.violated(
                                                field,
                                                field.getFullName() + " could carry personal data into params")));
                            }
                        })
                .because("ADR-043: params carry no personal data, so an error field is never free text");
    }

    /**
     * ADR-106 and NFR-19: a personal value reaches a log through its {@code toString()}, so every
     * concrete type marked {@link PersonalData} declares its own, which prints it redacted. javac
     * marks a record's implicit {@code toString()} final and an explicit one not, unless written
     * so, so a record's final {@code toString()} counts as missing.
     */
    static ArchRule personalValuesPrintRedacted(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("declare its own toString() when PersonalData") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (isPersonalValue(item) && !declaresItsOwnToString(item)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is PersonalData without its own toString()"));
                        }
                    }
                })
                .because("ADR-106: a personal value prints redacted, so it never reaches a log");
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

    /** Whether a type is a concrete event, which the event rules check. */
    static boolean isEvent(JavaClass type) {
        return isConcrete(type, DomainEvent.class);
    }

    /** Whether a type is a concrete personal value, which must print redacted. */
    static boolean isPersonalValue(JavaClass type) {
        return isConcrete(type, PersonalData.class);
    }

    /** Whether a type's constructors must be private. */
    static boolean isRestricted(JavaClass type) {
        return RestrictedTypes.isAggregate(type)
                || RestrictedTypes.readsUnderLock(type)
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
        if (raw.isArray() || raw.isAssignableTo(PersonalData.class)) {
            return false;
        }
        if (raw.isEquivalentTo(List.class)) {
            return listAllowed
                    && type instanceof JavaParameterizedType parameterized
                    && parameterized.getActualTypeArguments().size() == 1
                    && isAllowedEventType(parameterized.getActualTypeArguments().get(0), event, false);
        }
        return EVENT_NUMBERS_AND_FLAGS.contains(raw.getName())
                || (!listAllowed && EVENT_LIST_BOXES.contains(raw.getName()))
                || raw.isEquivalentTo(Instant.class)
                || (raw.isEnum() && raw.getPackageName().equals(event.getPackageName()))
                || EVENT_KERNEL_TYPES.contains(raw.getName());
    }

    private static boolean isForbiddenParameter(JavaClass type) {
        return type.isArray()
                || type.isEquivalentTo(Object.class)
                || type.isAssignableTo(CharSequence.class)
                || type.isEquivalentTo(PlainText.class)
                || type.isAssignableTo(PersonalData.class);
    }

    /**
     * Whether a type, once arrays are unwrapped, is marked {@link PersonalData}, or is a DispatchFlow
     * type with a field, at any depth of fields and type arguments, that is. An interface or an
     * abstract class is also searched through its subclasses, so a sealed type is personal when one
     * variant holds a personal value. {@code visited} holds the types already looked into, so a type
     * that refers to itself ends the search. It sees a nested type's fields and subclasses only when
     * they are imported, as the production check's whole-root import guarantees.
     */
    private static boolean holdsPersonalData(JavaClass type, Set<String> visited) {
        JavaClass owner = type.isArray() ? type.getBaseComponentType() : type;
        if (owner.isAssignableTo(PersonalData.class)) {
            return true;
        }
        if (!owner.getPackageName().startsWith(OWN_PACKAGES) || !visited.add(owner.getName())) {
            return false;
        }
        boolean hasVariants = owner.isInterface() || owner.getModifiers().contains(JavaModifier.ABSTRACT);
        return instanceFields(owner)
                        .flatMap(field -> erasuresIn(field.getType()))
                        .anyMatch(fieldType -> holdsPersonalData(fieldType, visited))
                || (hasVariants
                        && owner.getSubclasses().stream().anyMatch(variant -> holdsPersonalData(variant, visited)));
    }

    private static boolean declaresItsOwnToString(JavaClass type) {
        boolean isRecord = type.isAssignableTo(Record.class);
        return type.getMethods().stream()
                .anyMatch(method -> method.getName().equals(TO_STRING)
                        && method.getRawParameterTypes().isEmpty()
                        && !(isRecord && method.getModifiers().contains(JavaModifier.FINAL)));
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
