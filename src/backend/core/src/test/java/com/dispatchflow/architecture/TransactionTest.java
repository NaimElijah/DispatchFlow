package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The transaction rules of ADR-129, checked against the production classes. The guard below fails
 * if the lock-taking methods stop being picked, which would let the lock rule pass on nothing.
 */
class TransactionTest {

    private static final String ROOT = ProductionClasses.ROOT;

    @Test
    void GivenTheProductionClasses_WhenTransactionsAreChecked_ThenOnlyTheServicesStartOne() {
        TransactionRules.transactionsStartOnlyInServices(ROOT).check(ProductionClasses.ALL);
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
}
