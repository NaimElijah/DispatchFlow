package com.dispatchflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The rules on where a transaction starts (ADR-129), built for a root package. {@code
 * com.dispatchflow} checks production code; {@code com.dispatchflow.archfixture} checks the fixtures
 * under {@code archfixture.theta} that prove each rule fires. A module's services are the classes
 * of its {@code application.service} packages, so a module added later is covered without editing a
 * rule.
 */
final class TransactionRules {

    /**
     * The ports whose calls leave the process, by simple name: HTTP to the optimizer, OSRM, the
     * geocoder, SMS, email and push, the live-update streams, and the proof files on disk. No
     * transaction waits on one (ADR-063, ADR-129 decision 3), so a port added to that list must be
     * added here.
     */
    static final Set<String> OUTSIDE_CALL_PORTS = Set.of(
            "RouteOptimizer",
            "FeasibilityChecker",
            "TravelTimes",
            "RouteShapes",
            "Geocoder",
            "RoadSnap",
            "SmsGateway",
            "EmailSender",
            "PushNotifier",
            "LiveUpdates",
            "BaselinePlanner",
            "ProofStore");

    /**
     * The ports that store nothing, by simple name: the clock, new IDs, the revocation cache that
     * only evicts, and the key a token is signed with. A service that opens no transaction of its own
     * may hold them (rule 7).
     */
    static final Set<String> STORELESS_PORTS = Set.of("Clock", "IdGenerator", "RevocationCache", "TokenSigner");

    /** The port every changing use case writes its audit record through (NFR-23). */
    static final String AUDIT_LOG = "AuditLog";

    /** Audit's helper that runs an attempt and records the refusal that leaves it (ADR-150). */
    static final String REFUSALS = "Refusals";

    /** The suffix of the class that holds a two-class use case's transaction (ADR-129 decision 3). */
    static final String CHANGE_SUFFIX = "Change";

    private TransactionRules() {}

    /**
     * Rule 1: only the service layer carries {@code @Transactional}. No class outside a module's
     * {@code application.service} packages, nor any of its methods, carries it, directly or through
     * an annotation that carries it, as Spring Modulith's {@code @ApplicationModuleListener} does.
     */
    static ArchRule transactionsStartOnlyInServices(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("carry no @Transactional outside the services") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (inServices(root, item)) {
                            return;
                        }
                        if (transactional(item)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " carries @Transactional outside the services"));
                        }
                        transactionalMembers(item)
                                .forEach(member -> events.add(SimpleConditionEvent.violated(
                                        member,
                                        member.getFullName() + " carries @Transactional outside the services")));
                    }
                })
                .because("ADR-129 decision 1: a transaction starts only in the service layer");
    }

    /**
     * Rule 2: every service is transactional, at class level, except one that opens no transaction
     * of its own. That is a use case split around its outside calls, or into one transaction per item,
     * which holds its {@code <UseCase>Change}. It is also a service that calls such a service,
     * directly or through an {@code api} or {@code spi} it implements, as a later call. A transaction
     * around that call would hold the callee's pushes, live updates and cache evictions until its own
     * commit, and change two aggregates in one transaction (ADR-063). Such a service carries no
     * {@code @Transactional}. A call to its own method would also go around Spring's proxy.
     */
    static ArchRule everyServiceIsTransactional(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("be transactional, unless it opens no transaction of its own") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isService(root, item)) {
                            return;
                        }
                        if (opensNoTransaction(root, item)) {
                            if (transactional(item)
                                    || transactionalMembers(item).findAny().isPresent()) {
                                events.add(SimpleConditionEvent.violated(
                                        item,
                                        item.getName()
                                                + " opens no transaction of its own (it holds its Change, or"
                                                + " calls a service that does), so it must carry no"
                                                + " @Transactional"));
                            }
                            return;
                        }
                        if (!item.isAnnotatedWith(Transactional.class)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is a service without a class-level @Transactional"));
                        }
                    }
                })
                .because("ADR-129 decisions 2 and 3: every service is transactional, or opens none of its own");
    }

    /**
     * Rule 7: a service that opens no transaction of its own (rule 2) touches no data. Every field it
     * holds is one of these:
     * <ul>
     *   <li>its {@code Change};
     *   <li>an outside-call port;
     *   <li>a port that stores nothing, {@link #STORELESS_PORTS};
     *   <li>another service;
     *   <li>an {@code api} or {@code spi}, {@code AuditLog} included.
     * </ul>
     * Each of those opens its own transaction, or needs none. A repository or a query port here
     * would read or write with no transaction. The audit log may be held to record a refusal once
     * the {@code Change} has rolled back, and rule 9 keeps a success off it (ADR-149 decision 3).
     */
    static ArchRule aServiceWithoutATransactionTouchesNoData(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("touch no data while it opens no transaction") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!isService(root, item) || !opensNoTransaction(root, item)) {
                            return;
                        }
                        instanceFields(item)
                                .filter(field -> !reachesNoDataItself(root, field.getRawType()))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field,
                                        field.getFullName()
                                                + " reaches data outside any transaction; move it to "
                                                + item.getSimpleName() + CHANGE_SUFFIX)));
                    }
                })
                .because("ADR-129 decision 3: the use case never touches the database itself");
    }

    /**
     * Rule 8: every transactional event listener is {@code @Async}, directly or through an
     * annotation. A synchronous {@code AFTER_COMMIT} listener runs while the committed transaction
     * still looks open on its thread. The service it calls would join that finished transaction,
     * and its writes would be lost when the connection is released (ADR-129 decision 7).
     */
    static ArchRule everyListenerIsAsync(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("run each transactional event listener asynchronously") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        item.getMethods().stream()
                                .filter(method -> carries(method, TransactionalEventListener.class))
                                .filter(method -> !carries(method, Async.class) && !carries(item, Async.class))
                                .forEach(method -> events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + " listens after a commit but isn't @Async")));
                    }
                })
                .because("ADR-129 decision 7: a listener has no transaction of its own");
    }

    /**
     * Rule 9: only a transactional service records a success, so the record commits with its change
     * or not at all (NFR-23, ADR-149 decision 3). A call to {@code AuditLog.succeeded}, or a
     * reference to it, sits in a class of a module's {@code application.service} packages that
     * carries {@code @Transactional} itself, at the default propagation, and in no method of it that
     * is read-only or names another propagation: a read-only transaction never flushes, so its record
     * would be lost. A class nested in a service, a lambda included, counts as the service, so a
     * success handed to work that runs later is for review. The rule reads the method that makes the
     * call, so a read-only method that reaches the call through a helper of its own passes it; at
     * runtime {@code JpaAuditRecords.append} then refuses the read-only transaction, loudly. A
     * service with no transaction, an adapter or the call wrapper records only a refusal, after
     * every transaction of the call has ended.
     */
    static ArchRule successIsRecordedOnlyInATransactionalService(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("record a success only inside a transactional service") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        JavaClass outermost = outermost(item);
                        boolean transactional = isService(root, outermost) && opensAReadWriteTransaction(outermost);
                        auditAccesses(item, "succeeded")
                                .filter(access -> !transactional || !keepsTheClassTransaction(access.getOrigin()))
                                .forEach(access -> events.add(SimpleConditionEvent.violated(
                                        access,
                                        access.getDescription()
                                                + " records a success outside a read-write transaction of a"
                                                + " service; move it into the " + CHANGE_SUFFIX
                                                + " that holds the change")));
                    }
                })
                .because("ADR-149 decision 3: a success commits with its change");
    }

    /**
     * Rule 10: no transactional class records a refusal, but one of {@code committedHere}, and none
     * runs work through Audit's {@code Refusals}, so a refusal is recorded after its change has
     * rolled back and never lost with it (NFR-23, ADR-129 decision 5, ADR-149 decisions 1 and 2,
     * ADR-150 decision 5). A one-class service that can refuse splits instead, and its use case
     * records the refusal through the helper. The exceptions are the {@code Change}s that answer a
     * refusal without letting it leave, so their transaction commits, carrying the record and only
     * what the refusal itself must store: their names are listed, and a {@code Change} joins the
     * list only by that test, in review. The helper rethrows the refusal, which rolls its record
     * back, so the list never excuses it. A method that carries {@code @Transactional} itself counts
     * as a transactional class. The rule reads a class's own calls: a module's own intermediary that
     * records for a transactional caller is left to review.
     */
    static ArchRule refusalIsNeverRecordedInsideATransaction(String root, Set<String> committedHere) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("record a refusal only outside any transaction") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        JavaClass outermost = outermost(item);
                        boolean classTransaction = carries(outermost, Transactional.class);
                        boolean listed = committedHere.contains(outermost.getSimpleName());
                        Stream<JavaCodeUnitAccess<?>> direct = auditAccesses(item, "refused")
                                .filter(access -> (classTransaction && !listed) || inATransactionalMethod(access));
                        Stream<JavaCodeUnitAccess<?>> helper = helperAccesses(item)
                                .filter(access -> classTransaction || inATransactionalMethod(access));
                        Stream.concat(direct, helper)
                                .forEach(access -> events.add(SimpleConditionEvent.violated(
                                        access,
                                        access.getDescription()
                                                + " records a refusal inside a transaction the refusal rolls"
                                                + " back; record it in the use case, after its "
                                                + CHANGE_SUFFIX + " has refused")));
                    }
                })
                .because("ADR-149 decision 1 and ADR-150 decision 5: a refusal is recorded once its change"
                        + " has rolled back");
    }

    /**
     * The {@code Change}s whose transaction commits a refused attempt's record on purpose, because
     * no refusal leaves them, and each alone knows the account or session its record names (ADR-149
     * decision 2, ADR-150 decision 5): the recovery request that answers every address alike and
     * stores nothing beside its record, and the refresh whose refusal revokes the session, which
     * commits with its record or not at all.
     */
    static final Set<String> REFUSALS_COMMITTED_IN_THEIR_CHANGE =
            Set.of("RequestPasswordRecoveryChange", "RefreshSessionChange");

    /** The calls and method references from {@code item} to this method of the audit log. */
    private static Stream<JavaCodeUnitAccess<?>> auditAccesses(JavaClass item, String method) {
        return Stream.<JavaCodeUnitAccess<?>>concat(
                        item.getMethodCallsFromSelf().stream(), item.getMethodReferencesFromSelf().stream())
                .filter(access -> access.getName().equals(method) && isTheAuditLog(access.getTargetOwner()));
    }

    /** Whether the access sits in a method that carries {@code @Transactional} itself. */
    private static boolean inATransactionalMethod(JavaCodeUnitAccess<?> access) {
        return carries(access.getOrigin(), Transactional.class);
    }

    /** The calls and method references from {@code item} to Audit's {@code Refusals} helper. */
    private static Stream<JavaCodeUnitAccess<?>> helperAccesses(JavaClass item) {
        return Stream.<JavaCodeUnitAccess<?>>concat(
                        item.getMethodCallsFromSelf().stream(), item.getMethodReferencesFromSelf().stream())
                .filter(access -> access.getTargetOwner().getSimpleName().equals(REFUSALS)
                        && access.getTargetOwner().getPackageName().endsWith(".application.api"));
    }

    private static boolean isTheAuditLog(JavaClass owner) {
        return owner.getSimpleName().equals(AUDIT_LOG)
                || owner.getAllRawInterfaces().stream()
                        .anyMatch(type -> type.getSimpleName().equals(AUDIT_LOG));
    }

    /** Whether the class carries {@code @Transactional} at the default propagation, not read-only. */
    private static boolean opensAReadWriteTransaction(JavaClass type) {
        return carries(type, Transactional.class)
                && type.tryGetAnnotationOfType(Transactional.class)
                        .map(TransactionRules::readWriteAtTheDefaultPropagation)
                        .orElse(true);
    }

    /** Whether a method keeps its class's transaction: it names no read-only one and no other propagation. */
    private static boolean keepsTheClassTransaction(JavaCodeUnit method) {
        return method.tryGetAnnotationOfType(Transactional.class)
                .map(TransactionRules::readWriteAtTheDefaultPropagation)
                .orElse(true);
    }

    private static boolean readWriteAtTheDefaultPropagation(Transactional transactional) {
        return !transactional.readOnly() && transactional.propagation() == Propagation.REQUIRED;
    }

    private static JavaClass outermost(JavaClass item) {
        JavaClass outermost = item;
        while (outermost.getEnclosingClass().isPresent()) {
            outermost = outermost.getEnclosingClass().get();
        }
        return outermost;
    }

    private static boolean carries(CanBeAnnotated element, Class<? extends Annotation> type) {
        return element.isAnnotatedWith(type) || element.isMetaAnnotatedWith(type);
    }

    /**
     * Whether a service opens no transaction of its own: it holds its {@code <UseCase>Change}, or it
     * holds a service that opens none, directly or through an interface such a service implements.
     */
    static boolean opensNoTransaction(String root, JavaClass item) {
        return opensNoTransaction(root, item, new HashSet<>());
    }

    /** {@code path} holds the services on the way here, so a cycle of services ends the search. */
    private static boolean opensNoTransaction(String root, JavaClass item, Set<String> path) {
        if (changeOf(item).isPresent()) {
            return true;
        }
        if (!path.add(item.getName())) {
            return false;
        }
        boolean found = instanceFields(item)
                .map(JavaField::getRawType)
                .anyMatch(type ->
                        callees(root, type).stream().anyMatch(callee -> opensNoTransaction(root, callee, path)));
        path.remove(item.getName());
        return found;
    }

    /** The services a field of this type reaches: the type itself, or the services implementing it. */
    private static Set<JavaClass> callees(String root, JavaClass type) {
        if (type.isInterface()) {
            Set<JavaClass> implementations = new HashSet<>();
            type.getAllSubclasses().stream()
                    .filter(subclass -> isService(root, subclass))
                    .forEach(implementations::add);
            return implementations;
        }
        return isService(root, type) ? Set.of(type) : Set.of();
    }

    private static boolean reachesNoDataItself(String root, JavaClass type) {
        String name = type.getSimpleName();
        if (OUTSIDE_CALL_PORTS.contains(name) || STORELESS_PORTS.contains(name)) {
            return true;
        }
        if (isService(root, type) || inServices(root, type)) {
            return true;
        }
        String below = type.getPackageName().startsWith(root + ".")
                ? type.getPackageName().substring(root.length() + 1)
                : "";
        String[] parts = below.split("\\.");
        return type.isInterface()
                && parts.length >= 3
                && parts[1].equals("application")
                && (parts[2].equals("api") || parts[2].equals("spi"));
    }

    /**
     * Rule 3: Spring can subclass every transactional class. In Spring Boot's class-based proxies a
     * {@code final} class can't be proxied at all, and a {@code final} method is skipped, so its call
     * runs with no transaction; a record is {@code final}.
     */
    static ArchRule transactionalClassesCanBeProxied(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("be a class Spring can subclass") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!transactional(item)
                                && transactionalMembers(item).findAny().isEmpty()) {
                            return;
                        }
                        if (item.isRecord() || item.getModifiers().contains(JavaModifier.FINAL)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is transactional but final, so it can't be proxied"));
                        }
                        item.getMethods().stream()
                                .filter(method -> method.getModifiers().contains(JavaModifier.FINAL))
                                .filter(method -> !method.getModifiers().contains(JavaModifier.STATIC))
                                .filter(method -> !method.getModifiers().contains(JavaModifier.PRIVATE))
                                .forEach(method -> events.add(SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName() + " is final, so its call skips the transaction")));
                    }
                })
                .because("ADR-129 decision 2: Spring's proxy is a subclass");
    }

    /**
     * Rule 4: no transactional class holds a port whose calls leave the process, {@code ports} by
     * simple name, so no database transaction waits on HTTP or disk.
     */
    static ArchRule noTransactionWaitsOnAnOutsideCall(String root, Set<String> ports) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("hold no outside-call port while transactional") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!transactional(item)
                                && transactionalMembers(item).findAny().isEmpty()) {
                            return;
                        }
                        instanceFields(item)
                                .filter(field ->
                                        ports.contains(field.getRawType().getSimpleName()))
                                .forEach(field -> events.add(SimpleConditionEvent.violated(
                                        field,
                                        field.getFullName() + " is an outside call inside a transaction; move it to "
                                                + "the use case and the rest to its " + CHANGE_SUFFIX)));
                    }
                })
                .because("ADR-063 and ADR-129 decision 3: outside calls come before the transaction");
    }

    /**
     * Rule 5: a service that writes an audit record changes something, so it is never read-only.
     * The other way round, that a service which changes nothing is read-only, is for review: a
     * wrongly read-only one fails at its first write.
     */
    static ArchRule anAuditingServiceIsNeverReadOnly(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("not be read-only while holding " + AUDIT_LOG) {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean readOnly = item.tryGetAnnotationOfType(Transactional.class)
                                .map(Transactional::readOnly)
                                .orElse(false);
                        if (readOnly
                                && instanceFields(item)
                                        .anyMatch(field -> field.getRawType()
                                                .getSimpleName()
                                                .equals(AUDIT_LOG))) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " is read-only but writes an audit record"));
                        }
                    }
                })
                .because("ADR-129 decision 4: a changing service is never read-only");
    }

    /**
     * Rule 6: a persistence adapter's method that calls a query taking a lock that lasts only as
     * long as its transaction, a {@code pg_advisory_xact_lock} or a {@code FOR UPDATE}, first calls
     * {@code OpenTransaction.require}. With no transaction open, the database would release the lock
     * when its statement ends, and the check it guards would protect nothing.
     */
    static ArchRule lockTakingMethodsRequireATransaction(String root) {
        return classes()
                .that()
                .resideInAPackage(root + "..")
                .should(new ArchCondition<JavaClass>("call OpenTransaction.require before a transaction-scoped lock") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.getPackageName().contains(".adapter.out.persistence")) {
                            return;
                        }
                        item.getMethods().forEach(method -> {
                            OptionalInt firstLock = method.getMethodCallsFromSelf().stream()
                                    .filter(TransactionRules::takesATransactionScopedLock)
                                    .mapToInt(JavaMethodCall::getLineNumber)
                                    .min();
                            if (firstLock.isEmpty()) {
                                return;
                            }
                            boolean checkedFirst = method.getMethodCallsFromSelf().stream()
                                    .filter(call -> call.getTargetOwner().isEquivalentTo(OpenTransaction.class)
                                            && call.getName().equals("require"))
                                    .anyMatch(call -> call.getLineNumber() < firstLock.getAsInt());
                            if (!checkedFirst) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName()
                                                + " takes a transaction-scoped lock without calling"
                                                + " OpenTransaction.require first"));
                            }
                        });
                    }
                })
                .because("ADR-129 decision 6: a lock taken outside a transaction protects nothing");
    }

    /**
     * Whether a call reaches a query whose SQL takes a lock that lasts only as long as its
     * transaction, read from its {@code @Query}: an advisory transaction lock, tried or waited for, or
     * a row lock of any strength ({@code FOR UPDATE}, {@code FOR NO KEY UPDATE}, {@code FOR SHARE},
     * {@code FOR KEY SHARE}), however its words are spaced.
     */
    static boolean takesATransactionScopedLock(JavaMethodCall call) {
        return call.getTarget()
                .resolveMember()
                .flatMap(target -> target.tryGetAnnotationOfType(Query.class))
                .map(query -> TRANSACTION_SCOPED_LOCK.matcher(query.value()).find())
                .orElse(false);
    }

    private static final Pattern TRANSACTION_SCOPED_LOCK = Pattern.compile(
            "\\bPG_(TRY_)?ADVISORY_XACT_LOCK(_SHARED)?\\b|\\bFOR\\s+((NO\\s+KEY\\s+)?UPDATE|(KEY\\s+)?SHARE)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Whether a class is a service: a public top-level class in a module's {@code
     * application.service} packages that holds a port, an {@code api} or another service. A value,
     * an exception, a sealed result or a package-private helper there is not one.
     */
    static boolean isService(String root, JavaClass item) {
        return inServices(root, item)
                && item.isTopLevelClass()
                && item.getModifiers().contains(JavaModifier.PUBLIC)
                && !item.isInterface()
                && !item.isEnum()
                && !item.isRecord()
                && !item.isAnnotation()
                && !item.isAssignableTo(Throwable.class)
                && instanceFields(item).anyMatch(field -> holdsADependency(root, field));
    }

    private static boolean holdsADependency(String root, JavaField field) {
        JavaClass type = field.getRawType();
        return type.getPackageName().startsWith(root + ".") && (type.isInterface() || inServices(root, type));
    }

    private static boolean inServices(String root, JavaClass item) {
        String below = item.getPackageName().startsWith(root + ".")
                ? item.getPackageName().substring(root.length() + 1)
                : "";
        String[] parts = below.split("\\.");
        return parts.length >= 3 && parts[1].equals("application") && parts[2].equals("service");
    }

    /** The {@code <UseCase>Change} a split use case holds: a field of that type in its own package. */
    private static Optional<JavaClass> changeOf(JavaClass item) {
        String name = item.getName() + CHANGE_SUFFIX;
        return instanceFields(item)
                .map(JavaField::getRawType)
                .filter(type -> type.getName().equals(name))
                .findFirst();
    }

    /**
     * Whether an element carries a transaction, directly or through an annotation: Spring's
     * {@code @Transactional}, or the {@code jakarta.transaction} one that hibernate-core brings and
     * Spring honours too.
     */
    private static boolean transactional(CanBeAnnotated element) {
        return carries(element, Transactional.class) || carries(element, jakarta.transaction.Transactional.class);
    }

    private static Stream<JavaMember> transactionalMembers(JavaClass item) {
        return item.getMembers().stream().filter(TransactionRules::transactional);
    }

    private static Stream<JavaField> instanceFields(JavaClass item) {
        return item.getFields().stream().filter(field -> !field.getModifiers().contains(JavaModifier.STATIC));
    }
}
