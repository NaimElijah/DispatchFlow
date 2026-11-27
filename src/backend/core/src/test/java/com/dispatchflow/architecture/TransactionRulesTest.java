package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.archfixture.theta.adapter.in.listen.AuditsASuccess;
import com.dispatchflow.archfixture.theta.adapter.in.listen.AuditsThroughTheService;
import com.dispatchflow.archfixture.theta.adapter.in.listen.HandsOnASuccess;
import com.dispatchflow.archfixture.theta.adapter.in.listen.ListenerInATransaction;
import com.dispatchflow.archfixture.theta.adapter.in.listen.ListensAsync;
import com.dispatchflow.archfixture.theta.adapter.in.listen.ListensWithoutAsync;
import com.dispatchflow.archfixture.theta.adapter.in.listen.OpensATransaction;
import com.dispatchflow.archfixture.theta.adapter.in.listen.PlainListener;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.JakartaTransactionalLedgers;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LedgersWithATransactionalMethod;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksARowWithoutTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksAfterTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksBeforeTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.LocksWithoutTheCheck;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.PlainLedgers;
import com.dispatchflow.archfixture.theta.adapter.out.persistence.TransactionalLedgers;
import com.dispatchflow.archfixture.theta.application.api.AuditLog;
import com.dispatchflow.archfixture.theta.application.api.Refusals;
import com.dispatchflow.archfixture.theta.application.api.Solve;
import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import com.dispatchflow.archfixture.theta.application.port.out.RouteOptimizer;
import com.dispatchflow.archfixture.theta.application.service.AnswersAlikeChange;
import com.dispatchflow.archfixture.theta.application.service.AnswersAlikeThroughTheHelperChange;
import com.dispatchflow.archfixture.theta.application.service.AuditLogService;
import com.dispatchflow.archfixture.theta.application.service.CallsASplitApi;
import com.dispatchflow.archfixture.theta.application.service.CallsASplitUseCase;
import com.dispatchflow.archfixture.theta.application.service.CountsLedgers;
import com.dispatchflow.archfixture.theta.application.service.DelegatesAndAudits;
import com.dispatchflow.archfixture.theta.application.service.DelegatesAndRecordsItsRefusal;
import com.dispatchflow.archfixture.theta.application.service.DelegatesAndRecordsItsRefusalThroughTheHelper;
import com.dispatchflow.archfixture.theta.application.service.DelegatesToASplitApi;
import com.dispatchflow.archfixture.theta.application.service.FinalMethodService;
import com.dispatchflow.archfixture.theta.application.service.FinalService;
import com.dispatchflow.archfixture.theta.application.service.ForgetsItsTransaction;
import com.dispatchflow.archfixture.theta.application.service.LedgerCount;
import com.dispatchflow.archfixture.theta.application.service.NestsASuccessWithNoTransaction;
import com.dispatchflow.archfixture.theta.application.service.ReadOnlyButAuditing;
import com.dispatchflow.archfixture.theta.application.service.ReadsLedgers;
import com.dispatchflow.archfixture.theta.application.service.RecordsASuccessInALambdaOfAReadOnlyRead;
import com.dispatchflow.archfixture.theta.application.service.RecordsASuccessInAReadOnlyRead;
import com.dispatchflow.archfixture.theta.application.service.RecordsASuccessWithoutATransaction;
import com.dispatchflow.archfixture.theta.application.service.RecordsInANestedClass;
import com.dispatchflow.archfixture.theta.application.service.RecordsItsRefusalInANestedClass;
import com.dispatchflow.archfixture.theta.application.service.RecordsItsRefusalInsideItsTransaction;
import com.dispatchflow.archfixture.theta.application.service.RecordsItsRefusalThroughTheHelperInsideItsTransaction;
import com.dispatchflow.archfixture.theta.application.service.SolvesLedgers;
import com.dispatchflow.archfixture.theta.application.service.SolvesLedgersChange;
import com.dispatchflow.archfixture.theta.application.service.SplitButHoldsLedgers;
import com.dispatchflow.archfixture.theta.application.service.SplitButHoldsLedgersChange;
import com.dispatchflow.archfixture.theta.application.service.SplitButTransactional;
import com.dispatchflow.archfixture.theta.application.service.SplitButTransactionalChange;
import com.dispatchflow.archfixture.theta.application.service.SplitWithATransactionalMethod;
import com.dispatchflow.archfixture.theta.application.service.SplitWithATransactionalMethodChange;
import com.dispatchflow.archfixture.theta.application.service.TransactionalOnAMethodOnly;
import com.dispatchflow.archfixture.theta.application.service.TransactionalRecord;
import com.dispatchflow.archfixture.theta.application.service.WaitsOnTheOptimizer;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
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
        Ledgers.class,
        RouteOptimizer.class,
        AuditLog.class,
        Refusals.class,
        Solve.class,
        LedgerCount.class,
        SolvesLedgers.class
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
                JakartaTransactionalLedgers.class,
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
    void GivenASplitUseCaseWithATransactionalMethod_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT),
                        SplitWithATransactionalMethod.class,
                        SplitWithATransactionalMethodChange.class))
                .isTrue();
    }

    @Test
    void GivenAServiceTransactionalOnAMethodOnly_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT), TransactionalOnAMethodOnly.class))
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

    @Test
    void GivenAServiceWithoutATransactionThatTouchesData_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT),
                        SplitButHoldsLedgers.class,
                        SplitButHoldsLedgersChange.class))
                .isTrue();
    }

    @Test
    void GivenAUseCaseWithoutATransactionThatRecordsItsRefusal_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        TransactionRules.everyServiceIsTransactional(FIXTURE_ROOT),
                        DelegatesAndRecordsItsRefusal.class))
                .isFalse();
        assertThat(flags(
                        TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT),
                        DelegatesAndRecordsItsRefusal.class))
                .isFalse();
        assertThat(flags(
                        TransactionRules.successIsRecordedOnlyInATransactionalService(FIXTURE_ROOT),
                        DelegatesAndRecordsItsRefusal.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                DelegatesAndAudits.class,
                AuditsASuccess.class,
                HandsOnASuccess.class,
                AuditsThroughTheService.class,
                RecordsASuccessInAReadOnlyRead.class,
                RecordsASuccessInALambdaOfAReadOnlyRead.class,
                RecordsASuccessWithoutATransaction.class
            })
    void GivenASuccessRecordedOutsideAReadWriteTransactionOfAService_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(
                        TransactionRules.successIsRecordedOnlyInATransactionalService(FIXTURE_ROOT),
                        fixture,
                        AuditLogService.class))
                .isTrue();
    }

    @Test
    void GivenSuccessesRecordedOnlyInTransactionalServices_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.successIsRecordedOnlyInATransactionalService(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @Test
    void GivenASuccessRecordedInANestedClass_WhenChecked_ThenItCountsAsTheClassItSitsIn() {
        assertThat(flags(
                        TransactionRules.successIsRecordedOnlyInATransactionalService(FIXTURE_ROOT),
                        RecordsInANestedClass.class,
                        RecordsInANestedClass.Step.class))
                .isFalse();
        assertThat(flags(
                        TransactionRules.successIsRecordedOnlyInATransactionalService(FIXTURE_ROOT),
                        NestsASuccessWithNoTransaction.class,
                        NestsASuccessWithNoTransaction.Step.class))
                .isTrue();
    }

    @Test
    void GivenATransactionalServiceThatRecordsItsRefusal_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        RecordsItsRefusalInsideItsTransaction.class))
                .isTrue();
    }

    @Test
    void GivenATransactionalServiceThatRunsItsWorkThroughTheRefusalHelper_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        RecordsItsRefusalThroughTheHelperInsideItsTransaction.class))
                .isTrue();
    }

    @Test
    void GivenAListedChangeThatRunsItsWorkThroughTheRefusalHelper_WhenChecked_ThenTheListDoesNotExcuseIt() {
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(
                                FIXTURE_ROOT, Set.of(AnswersAlikeThroughTheHelperChange.class.getSimpleName())),
                        AnswersAlikeThroughTheHelperChange.class))
                .isTrue();
    }

    @Test
    void GivenARefusalRecordedInANestedClassOfATransactionalService_WhenChecked_ThenItCountsAsTheService() {
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        RecordsItsRefusalInANestedClass.class,
                        RecordsItsRefusalInANestedClass.Step.class))
                .isTrue();
    }

    @Test
    void GivenAChangeThatAnswersARefusalAlike_WhenChecked_ThenItIsFlaggedUnlessListed() {
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        AnswersAlikeChange.class))
                .isTrue();
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(
                                FIXTURE_ROOT, Set.of(AnswersAlikeChange.class.getSimpleName())),
                        AnswersAlikeChange.class))
                .isFalse();
    }

    @Test
    void GivenRefusalsRecordedOnlyOutsideATransaction_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()), CLEAN))
                .isFalse();
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        DelegatesAndRecordsItsRefusal.class))
                .isFalse();
        assertThat(flags(
                        TransactionRules.refusalIsNeverRecordedInsideATransaction(FIXTURE_ROOT, Set.of()),
                        DelegatesAndRecordsItsRefusalThroughTheHelper.class))
                .isFalse();
    }

    @Test
    void GivenSplitUseCasesThatTouchNoData_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.aServiceWithoutATransactionTouchesNoData(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {FinalService.class, FinalMethodService.class, TransactionalRecord.class})
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

    @ParameterizedTest
    @ValueSource(classes = {LocksBeforeTheCheck.class, LocksARowWithoutTheCheck.class})
    void GivenAnAdapterLockingBeforeOrWithoutTheCheck_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(TransactionRules.lockTakingMethodsRequireATransaction(FIXTURE_ROOT), fixture, LEDGER_ROWS))
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
    void GivenAListenerThatRunsSynchronouslyAfterTheCommit_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(TransactionRules.everyListenerIsAsync(FIXTURE_ROOT), ListensWithoutAsync.class))
                .isTrue();
    }

    @Test
    void GivenAnAsyncListener_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.everyListenerIsAsync(FIXTURE_ROOT), ListensAsync.class, PlainListener.class))
                .isFalse();
    }

    @Test
    void GivenReadOnlyServicesThatDoNotAudit_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(TransactionRules.anAuditingServiceIsNeverReadOnly(FIXTURE_ROOT), CLEAN))
                .isFalse();
    }
}
