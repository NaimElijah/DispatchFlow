package com.dispatchflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A use case that carries {@code @Transactional} becomes a bean through Spring Boot's class-based
 * proxy, which must subclass it: so it is never {@code final} (ADR-129, ADR-140 decision 10).
 * {@code TransactionRules} rule 3 checks the production classes; this shows why, in the pinned
 * Spring Boot.
 */
class TransactionalBeanTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, TransactionAutoConfiguration.class))
            .withBean(PlatformTransactionManager.class, StubTransactionManager::new);

    @Test
    void GivenATransactionalUseCaseBeanThatIsNotFinal_WhenTheContextStarts_ThenItIsProxiedAndRunsInATransaction() {
        runner.withBean(OpenUseCase.class).run(context -> {
            assertThat(context).hasNotFailed();
            OpenUseCase useCase = context.getBean(OpenUseCase.class);

            assertThat(AopUtils.isCglibProxy(useCase)).isTrue();
            assertThat(useCase.runsInATransaction()).isTrue();
        });
    }

    @Test
    void GivenATransactionalUseCaseBeanThatIsFinal_WhenTheContextStarts_ThenTheContextFailsToStart() {
        runner.withBean(FinalUseCase.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("final class")
                    .hasMessageContaining(FinalUseCase.class.getName());
        });
    }

    /** A use case as the modules write one: a plain class with no interface. */
    @Transactional
    static class OpenUseCase {

        public boolean runsInATransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    /** The same use case, {@code final}, which the class-based proxy can't subclass. */
    @Transactional
    static final class FinalUseCase {

        public boolean runsInATransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    /** A transaction manager with no resource. */
    static final class StubTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource to bind: the test only asks whether a transaction is active.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // Nothing to commit.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // Nothing to roll back.
        }
    }
}
