package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.archfixture.theta.adapter.in.listen.ListenerInATransaction;
import com.dispatchflow.archfixture.theta.adapter.in.listen.OpensATransaction;
import com.dispatchflow.archfixture.theta.adapter.in.listen.PlainListener;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LedgersWithATransactionalMethod;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksAfterTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksWithoutTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.PlainLedgers;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.TransactionalLedgers;
import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Solve;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import com.dispatchflow.archfixture.theta.application.port.out.RouteOptimizer;
import com.dispatchflow.archfixture.theta.application.service.CallsASplitApi;
import com.dispatchflow.archfixture.theta.application.service.CallsASplitUseCase;
import com.dispatchflow.archfixture.theta.application.service.CountsLedgers;
import com.dispatchflow.archfixture.theta.application.service.DelegatesAndAudits;
import com.dispatchflow.archfixture.theta.application.service.DelegatesToASplitApi;
import com.dispatchflow.archfixture.theta.application.service.FinalMethodService;
import com.dispatchflow.archfixture.theta.application.service.FinalService;
import com.dispatchflow.archfixture.theta.application.service.ForgetsItsTransaction;
import com.dispatchflow.archfixture.theta.application.service.LedgerCount;
import com.dispatchflow.archfixture.theta.application.service.ReadOnlyButAuditing;
import com.dispatchflow.archfixture.theta.application.service.ReadsLedgers;
import com.dispatchflow.archfixture.theta.application.service.SolvesLedgers;
import com.dispatchflow.archfixture.theta.application.service.SolvesLedgersChange;
import com.dispatchflow.archfixture.theta.application.service.SplitButHoldsLedgers;
import com.dispatchflow.archfixture.theta.application.service.SplitButHoldsLedgersChange;
import com.dispatchflow.archfixture.theta.application.service.SplitButTransactional;
import com.dispatchflow.archfixture.theta.application.service.SplitButTransactionalChange;
import com.dispatchflow.archfixture.theta.application.service.WaitsOnTheOptimizer;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Each rule of {@link TransactionRules} flags its deliberately wrong fixture under {@code
 * archfixture.theta} and passes the clean ones.
 */
class TransactionRulesTest {

    private static final String FIXTURE_ROOT = FixtureClasses.ROOT;

    /** The package-private queries the theta adapters call, named so the rule can read their SQL. */
    private static final Class<?> LEDGER_ROWS = ledgerRows();

    /** The ports and values every fixture names, so a rule sees the types its fields hold. */
    private static final Class<?>[] PORTS = {
        Ledgers.class, RouteOptimizer.class, AuditLog.class, Solve.class, LedgerCount.class, SolvesLedgers.class
    };

    private static Class<?> ledgerRows() {
        try {
            return Class.forName("com.dispatchflow.archfixture.theta.adapter.out.persistence.LedgerRows");
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("the theta fixtures lost LedgerRows", missing);
        }
    }

    private static boolean flags(ArchRule rule, Class<?>... fixtures) {
        Class<?>[] all = new Class<?>[fixtures.length + PORTS.length];
        System.arraycopy(fixtures, 0, all, 0, fixtures.length);
        System.arraycopy(PORTS, 0, all, fixtures.length, PORTS.length);
        return rule.evaluate(FixtureClasses.only(all)).hasViolation();
    }

    private static final Class<?>[] CLEAN = {
        CountsLedgers.class,
        ReadsLedgers.class,
        SolvesLedgers.class,
        SolvesLedgersChange.class,
        PlainLedgers.class,
        PlainListener.class
    };

    @ParameterizedTest
    @ValueSource(
            classes = {
                TransactionalLedgers.class,
                LedgersWithATransactionalMethod.class,
                ListenerInATransaction.class,
                OpensATransaction.class
            })
    void GivenATransactionOutsideTheServices_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(TransactionRules.transactionsStartOnlyInServices(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenTransactionsOnlyInTheServices_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.transactionsStartOnlyInServices(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @Test
    void GivenAServiceWithNoTransaction_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT), ForgetsItsTransaction.class))
                .isTrue();
    }

    @Test
    void GivenASplitUseCaseThatOpensATransactionToo_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT),
                        SplitButTransactional.class,
                        SplitButTransactionalChange.class))
                .isTrue();
    }

    @Test
    void GivenTransactionalServicesASplitUseCaseAndAValue_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {CallsASplitUseCase.class, CallsASplitApi.class})
    void GivenATransactionalServiceCallingASplitUseCase_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenADelegateCallingASplitUseCaseWithNoTransaction_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT), DelegatesToASplitApi.class))
                .isFalse();
        assertThat(flags(
                        TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT),
                        DelegatesToASplitApi.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {SplitButHoldsLedgers.class, DelegatesAndAudits.class})
    void GivenAServiceWithoutATransactionThatTouchesData_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(
                        TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT),
                        fixture,
                        SplitButHoldsLedgersChange.class))
                .isTrue();
    }

    @Test
    void GivenSplitUseCasesThatTouchNoData_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {FinalService.class, FinalMethodService.class})
    void GivenAFinalTransactionalClassOrMethod_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(TransactionRules.transactionalClassesCanBeProxied(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenTransactionalClassesSpringCanSubclass_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.transactionalClassesCanBeProxied(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @Test
    void GivenAFinalTransactionalClass_WhenSpringBuildsItsProxyClass_ThenItCannot() {
        ProxyFactory factory = new ProxyFactory();
        factory.setTargetClass(FinalService.class);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor());

        assertThatThrownBy(() -> factory.getProxyClass(FinalService.class.getClassLoader()))
                .isInstanceOf(AopConfigException.class);
    }

    @Test
    void GivenATransactionalServiceHoldingAnOutsideCallPort_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.noTransactionWaitsOnAnOutsideCall(
                                FIXTURE_ROOT, TransactionRules.OUTSIDE_CALL_PORTS),
                        WaitsOnTheOptimizer.class))
                .isTrue();
    }

    @Test
    void GivenOutsideCallsOnlyBeforeTheTransaction_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        TransactionRules.noTransactionWaitsOnAnOutsideCall(
                                FIXTURE_ROOT, TransactionRules.OUTSIDE_CALL_PORTS),
                        CLEAN))
                .isFalse();
    }

    @Test
    void GivenAReadOnlyServiceThatAudits_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(TransactionRules.anAuditingServiceIsNeverReadOnly(FIXTURE_ROOT), ReadOnlyButAuditing.class))
                .isTrue();
    }

    @Test
    void GivenAnAdapterTakingALockWithoutTheCheck_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.lockTakingMethodsRequireATransaction(FIXTURE_ROOT),
                        LocksWithoutTheCheck.class,
                        LEDGER_ROWS))
                .isTrue();
    }

    @Test
    void GivenAnAdapterCheckingBeforeItsLock_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        TransactionRules.lockTakingMethodsRequireATransaction(FIXTURE_ROOT),
                        LocksAfterTheCheck.class,
                        LEDGER_ROWS))
                .isFalse();
    }

    @Test
    void GivenReadOnlyServicesThatDoNotAudit_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.anAuditingServiceIsNeverReadOnly(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }
}
