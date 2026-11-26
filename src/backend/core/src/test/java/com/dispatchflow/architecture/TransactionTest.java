package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * The transaction rules of ADR-129, checked against the production classes, and Spring's own view
 * of every service: the proxy it would build, and the transaction it would open. The guard below
 * fails if the services stop being picked, which would let the rules pass on nothing.
 */
class TransactionTest {

    private static final String ROOT = ProductionClasses.ROOT;
    /** Fewer than the 180 transactional services and 90 split use cases there are today. */
    private static final int FEWEST_SERVICES = 250;

    private static List<JavaClass> services() {
        return ProductionClasses.ALL.stream()
                .filter(type -> TransactionRules.isService(ROOT, type))
                .toList();
    }

    @Test
    void GivenTheProductionClasses_WhenTheServicesArePicked_ThenEveryModulesAreAmongThem() {
        assertThat(services().stream().map(JavaClass::getName))
                .contains(
                        "com.dispatchflow.access.application.service.account.ChangeLanguage",
                        "com.dispatchflow.audit.application.service.AuditLogService",
                        "com.dispatchflow.fleet.application.service.register.ChangeDepotHours",
                        "com.dispatchflow.planning.application.service.plan.RequestSolve",
                        "com.dispatchflow.planning.application.service.plan.RequestSolveChange")
                .hasSizeGreaterThan(FEWEST_SERVICES);
    }

    @Test
    void GivenTheProductionClasses_WhenTheServicesArePicked_ThenEachModuleWithServicesHasSomePicked() {
        Set<String> modulesWithServices = ProductionClasses.ALL.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.matches("com\\.dispatchflow\\.\\w+\\.application\\.service(\\..*)?"))
                .map(TransactionTest::module)
                .collect(Collectors.toSet());

        assertThat(services().stream()
                        .map(service -> module(service.getPackageName()))
                        .collect(Collectors.toSet()))
                .containsAll(modulesWithServices)
                .hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void GivenTheNamedPorts_WhenLookedUp_ThenEachIsAPortInTheCode() {
        Set<String> ports = ProductionClasses.ALL.stream()
                .filter(JavaClass::isInterface)
                .filter(type -> type.getPackageName().contains(".application.port.out")
                        || type.getPackageName().endsWith(".shared.application"))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(ports)
                .containsAll(TransactionRules.OUTSIDE_CALL_PORTS)
                .containsAll(TransactionRules.STORELESS_PORTS);
    }

    private static String module(String packageName) {
        return packageName.split("\\.")[2];
    }

    @Test
    void GivenTheProductionClasses_WhenTransactionsAreChecked_ThenOnlyTheServicesStartOne() {
        TransactionRules.transactionsStartOnlyInServices(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheServicesAreChecked_ThenEachIsTransactionalOrHoldsItsChange() {
        TransactionRules.everyServiceIsTransactional(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenServicesWithoutATransactionAreChecked_ThenNoneTouchesData() {
        TransactionRules.aServiceWithoutATransactionTouchesNoData(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenSuccessesAreRecorded_ThenOnlyTransactionalServicesRecordThem() {
        TransactionRules.successIsRecordedOnlyInATransactionalService(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRefusalsAreRecorded_ThenNoTransactionalClassButTheListedRecordsOne() {
        TransactionRules.refusalIsNeverRecordedInsideATransaction(
                        ROOT, TransactionRules.REFUSALS_COMMITTED_IN_THEIR_CHANGE)
                .check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheChangesThatCommitARefusal_WhenLookedUp_ThenEachIsOneProductionService() {
        assertThat(TransactionRules.REFUSALS_COMMITTED_IN_THEIR_CHANGE)
                .allSatisfy(name -> assertThat(ProductionClasses.ALL.stream()
                                .filter(type -> type.getSimpleName().equals(name))
                                .filter(type -> TransactionRules.isService(ROOT, type)))
                        .as("the listed %s", name)
                        .hasSize(1));
    }

    @Test
    void GivenTheProductionClasses_WhenListenersAreChecked_ThenEachIsAsync() {
        TransactionRules.everyListenerIsAsync(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTransactionalClassesAreChecked_ThenSpringCanSubclassEach() {
        TransactionRules.transactionalClassesCanBeProxied(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTransactionalClassesAreChecked_ThenNoneHoldsAnOutsideCall() {
        TransactionRules.noTransactionWaitsOnAnOutsideCall(ROOT, TransactionRules.OUTSIDE_CALL_PORTS)
                .check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenReadOnlyServicesAreChecked_ThenNoneAudits() {
        TransactionRules.anAuditingServiceIsNeverReadOnly(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenLockTakingAdaptersAreChecked_ThenEachRequiresATransaction() {
        TransactionRules.lockTakingMethodsRequireATransaction(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheLockTakingMethodsArePicked_ThenTheyAreTheEight() {
        assertThat(ProductionClasses.ALL.stream()
                        .flatMap(type -> type.getMethods().stream())
                        .filter(method -> method.getMethodCallsFromSelf().stream()
                                .anyMatch(TransactionRules::takesATransactionScopedLock))
                        .map(method -> method.getOwner().getSimpleName() + "." + method.getName()))
                .containsExactlyInAnyOrder(
                        "JpaUserAccountRepository.activeAdminsUnderLock",
                        "JpaStopMessagesRepository.capOn",
                        "JpaStopMessagesRepository.countAnswer",
                        "JpaOrderRepository.capacityUnderLock",
                        "JpaSolveJobRepository.capacityUnderLock",
                        "JpaSolveJobRepository.claimNext",
                        "JpaAlertRepository.addUnlessKeyTaken",
                        "JpaPlanRepository.add");
    }

    /**
     * What the rules can't see: that Spring builds a class-based proxy for every transactional
     * service, and finds a transaction on each of its public methods, while a split use case has
     * none of its own. Only Audit's {@code AuditLogService} is wired yet (ADR-119), so this builds
     * each proxy class without a bean.
     */
    @Test
    void GivenEveryService_WhenSpringBuildsItsProxy_ThenTransactionalOnesOpenATransactionAndSplitOnesNone()
            throws ClassNotFoundException {
        TransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        for (JavaClass service : services()) {
            Class<?> type = Class.forName(service.getName());
            List<Method> methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .filter(method -> !Modifier.isStatic(method.getModifiers()))
                    .toList();
            if (!type.isAnnotationPresent(Transactional.class)) {
                assertThat(methods)
                        .as("%s opens no transaction of its own", type.getName())
                        .allSatisfy(method -> assertThat(attributes.getTransactionAttribute(method, type))
                                .isNull());
                continue;
            }
            ProxyFactory factory = new ProxyFactory();
            factory.setTargetClass(type);
            factory.setProxyTargetClass(true);
            factory.addAdvice(new TransactionInterceptor());
            assertThat(factory.getProxyClass(type.getClassLoader()))
                    .as("%s's proxy", type.getName())
                    .isNotEqualTo(type)
                    .isAssignableTo(type);
            assertThat(methods)
                    .as("%s's public methods", type.getName())
                    .allSatisfy(method -> assertThat(attributes.getTransactionAttribute(method, type))
                            .isNotNull());
        }
    }
}
