package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.dispatchflow.shared.AggregateRoot;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;

/**
 * The rules on how the domain is mapped for JPA (ADR-126), built for a root package. {@code
 * com.dispatchflow} checks production code; {@code com.dispatchflow.archfixture} checks the fixtures
 * that prove each rule fires. Each looks at every class under the root, so a module added later is
 * covered without editing a rule, and none needs a mapped class to exist before the first lane maps
 * its aggregate.
 */
final class PersistenceRules {

    private static final List<Class<? extends Annotation>> RELATIONSHIPS =
            List.of(OneToMany.class, ManyToOne.class, OneToOne.class, ManyToMany.class);
    private static final List<Class<? extends Annotation>> MAPPED_TYPES =
            List.of(Entity.class, MappedSuperclass.class, Embeddable.class);
    private static final List<Class<?>> WIDE_REPOSITORIES =
            List.of(CrudRepository.class, ListCrudRepository.class, JpaRepository.class);

    private PersistenceRules() {}

    /**
     * Relationships stay inside one aggregate: no relationship's target is in another module, or is
     * another aggregate's root or child. The rule first works out each entity's owners, the roots
     * that reach it through relationships without passing another root. A child's reference back to
     * the root that owns it is inside its aggregate, so it is allowed; two roots that refer to each
     * other are not, and neither is an entity two roots reach.
     */
    static ArchRule relationshipsStayInsideTheAggregate(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("relate only to entities of its own aggregate") {
                    private Map<JavaClass, Set<JavaClass>> owners = Map.of();

                    @Override
                    public void init(Collection<JavaClass> allClasses) {
                        owners = ownersOf(root, allClasses);
                    }

                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        relationshipFields(item)
                                .forEach(field -> targetsOf(root, field)
                                        .filter(target -> !insideTheAggregate(root, item, target, owners))
                                        .forEach(target -> events.add(SimpleConditionEvent.violated(
                                                field,
                                                field.getFullName() + " relates to " + target.getName()
                                                        + ", outside its aggregate"))));
                    }
                })
                .because("ADR-126 and DL §3.1: between aggregates and between modules there are only IDs");
    }

    /** Only {@code AggregateRoot} declares {@code @Version}, so a child entity never has its own. */
    static ArchRule onlyTheAggregateRootIsVersioned(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("declare no @Version") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (item.isEquivalentTo(AggregateRoot.class)) {
                            return;
                        }
                        item.getFields().stream()
                                .filter(field -> field.isAnnotatedWith(Version.class))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field, field.getFullName() + " is a @Version outside AggregateRoot")));
                    }
                })
                .because("ADR-126 and ADR-057: the root's version guards its whole aggregate");
    }

    /**
     * Every enum a mapped class stores, alone or in an element collection, is stored by name: the
     * default stores its position, which reordering the enum would silently corrupt.
     */
    static ArchRule enumsAreStoredByName(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("store every enum with @Enumerated(EnumType.STRING)") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isMapped(item)) {
                            return;
                        }
                        persistentFields(item)
                                .filter(PersistenceRules::storesAnEnum)
                                .filter(field -> !storedByName(field))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field, field.getFullName() + " stores an enum without EnumType.STRING")));
                    }
                })
                .because("ADR-126: a reordered enum must not change what a stored row means");
    }

    /**
     * Every entity root declares its own {@code @PostLoad} check, which runs the invariants its
     * {@code restore} used to and throws {@code StoredStateInvalid}.
     */
    static ArchRule everyEntityRootChecksWhatItLoads(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("declare a @PostLoad method when an entity root") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (item.isAnnotatedWith(Entity.class)
                                && RestrictedTypes.isAggregate(item)
                                && item.getMethods().stream()
                                        .noneMatch(method -> method.isAnnotatedWith(PostLoad.class))) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is an entity root with no @PostLoad check"));
                        }
                    }
                })
                .because("ADR-126 decision 8: a damaged row is refused when it loads");
    }

    /** No key is generated: every ID comes from {@code IdGenerator}. */
    static ArchRule noKeyIsGenerated(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("declare no @GeneratedValue") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        item.getFields().stream()
                                .filter(field -> field.isAnnotatedWith(GeneratedValue.class))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field, field.getFullName() + " is a generated key")));
                        item.getMethods().stream()
                                .filter(method -> method.isAnnotatedWith(GeneratedValue.class))
                                .forEach(method -> events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + " is a generated key")));
                    }
                })
                .because("ADR-126 decision 10 and DL §3.9 rule 3: IDs come from IdGenerator");
    }

    /**
     * No interface extends {@code CrudRepository}, {@code ListCrudRepository} or {@code
     * JpaRepository}: an adapter declares only {@code save} and the lookups its port needs.
     */
    static ArchRule noRepositoryOffersMoreThanItsPortNeeds(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("not extend CrudRepository, ListCrudRepository or JpaRepository") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        WIDE_REPOSITORIES.stream()
                                .filter(item::isAssignableTo)
                                .findFirst()
                                .ifPresent(wide -> events.add(SimpleConditionEvent.violated(
                                        item, item.getName() + " extends " + wide.getSimpleName())));
                    }
                })
                .because("ADR-126 decision 6: no saveAll, deleteAll or batch delete on an aggregate");
    }

    /**
     * Every entity names its own module's schema in its {@code @Table}, which is how each
     * persistence adapter is kept to that schema (ADR-041, ADR-040).
     */
    static ArchRule everyEntityLivesInItsModulesSchema(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("name its module's schema in @Table when an entity") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.isAnnotatedWith(Entity.class)) {
                            return;
                        }
                        String module = moduleOf(root, item.getPackageName());
                        Optional<String> schema =
                                item.tryGetAnnotationOfType(Table.class).map(Table::schema);
                        if (!schema.map(module::equals).orElse(false)) {
                            events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getName() + " is in schema " + schema.orElse("(none)") + ", not its module's, "
                                            + module));
                        }
                    }
                })
                .because("ADR-041: each module owns its tables, in its own PostgreSQL schema");
    }

    private static Stream<JavaField> relationshipFields(JavaClass item) {
        return item.getFields().stream().filter(field -> RELATIONSHIPS.stream().anyMatch(field::isAnnotatedWith));
    }

    /** The classes under the root that a relationship field names, its type arguments included. */
    private static Stream<JavaClass> targetsOf(String root, JavaField field) {
        return erasuresIn(field.getType()).filter(type -> type.getPackageName().startsWith(root + "."));
    }

    /**
     * Each entity's owners: the aggregate roots that reach it through relationship fields without
     * passing another root.
     */
    private static Map<JavaClass, Set<JavaClass>> ownersOf(String root, Collection<JavaClass> allClasses) {
        Map<JavaClass, Set<JavaClass>> owners = new HashMap<>();
        allClasses.stream().filter(RestrictedTypes::isAggregate).forEach(aggregate -> {
            Deque<JavaClass> toVisit = new ArrayDeque<>(List.of(aggregate));
            Set<JavaClass> reached = new HashSet<>();
            while (!toVisit.isEmpty()) {
                relationshipFields(toVisit.pop())
                        .flatMap(field -> targetsOf(root, field))
                        .filter(target -> !RestrictedTypes.isAggregate(target))
                        .filter(reached::add)
                        .forEach(target -> {
                            owners.computeIfAbsent(target, ignored -> new HashSet<>())
                                    .add(aggregate);
                            toVisit.push(target);
                        });
            }
        });
        return owners;
    }

    /**
     * Whether a relationship from {@code item} to {@code target} stays inside one aggregate. The
     * target is in the same module, and either it is the root that owns {@code item}, a child, or
     * it is a child owned by {@code item}'s aggregate alone.
     */
    private static boolean insideTheAggregate(
            String root, JavaClass item, JavaClass target, Map<JavaClass, Set<JavaClass>> owners) {
        if (!moduleOf(root, target.getPackageName()).equals(moduleOf(root, item.getPackageName()))) {
            return false;
        }
        Set<JavaClass> itemsAggregate =
                RestrictedTypes.isAggregate(item) ? Set.of(item) : owners.getOrDefault(item, Set.of());
        if (RestrictedTypes.isAggregate(target)) {
            return !RestrictedTypes.isAggregate(item) && itemsAggregate.contains(target);
        }
        Set<JavaClass> targetsOwners = owners.getOrDefault(target, Set.of());
        return targetsOwners.size() <= 1 && itemsAggregate.containsAll(targetsOwners);
    }

    private static boolean isMapped(JavaClass item) {
        return MAPPED_TYPES.stream().anyMatch(item::isAnnotatedWith);
    }

    private static Stream<JavaField> persistentFields(JavaClass item) {
        return item.getFields().stream()
                .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                .filter(field -> !field.getModifiers().contains(JavaModifier.TRANSIENT))
                .filter(field -> !field.isAnnotatedWith(Transient.class));
    }

    private static boolean storesAnEnum(JavaField field) {
        if (field.getRawType().isEnum()) {
            return true;
        }
        return field.isAnnotatedWith(ElementCollection.class)
                && erasuresIn(field.getType()).skip(1).anyMatch(JavaClass::isEnum);
    }

    private static boolean storedByName(JavaField field) {
        return field.tryGetAnnotationOfType(Enumerated.class)
                .map(enumerated -> enumerated.value() == EnumType.STRING)
                .orElse(false);
    }

    /**
     * The first package segment below the root: the module, or the kernel's {@code shared}; empty
     * for the root package itself.
     */
    private static String moduleOf(String root, String packageName) {
        return packageName.equals(root)
                ? ""
                : packageName.substring(root.length() + 1).split("\\.")[0];
    }

    /** The erasure of a type and of every type argument inside it, at any depth. */
    private static Stream<JavaClass> erasuresIn(JavaType type) {
        Stream<JavaClass> self = Stream.of(type.toErasure());
        if (type instanceof JavaParameterizedType parameterized) {
            return Stream.concat(
                    self, parameterized.getActualTypeArguments().stream().flatMap(PersistenceRules::erasuresIn));
        }
        return self;
    }
}
