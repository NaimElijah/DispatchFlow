package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.library.dependencies.Slices;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The package rules of ADR-118, checked against the production classes. The guards below fail when
 * a rule's subject picks no production package, which would let the rule pass on nothing.
 */
class DomainPackageTest {

    private static final String ROOT = ProductionClasses.ROOT;
    private static final List<String> MODULES =
            List.of("access", "audit", "fleet", "liveops", "messaging", "orders", "planning", "reporting");

    /**
     * Each module yields at least one slice today, its domain root, because no module is split yet.
     * Once ADR-118's splits land, each split module yields more than one; this asserts only the lower
     * bound, so it holds both before and after.
     */
    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreSliced_ThenEachModulesDomainRootIsASlice() {
        Slices slices =
                Slices.assignedFrom(DomainPackageRules.domainPackages(ROOT)).transform(ProductionClasses.ALL);

        assertThat(slices.stream()
                        .map(slice -> slice.iterator().next().getPackageName())
                        .toList())
                .containsAll(MODULES.stream()
                        .map(module -> ROOT + "." + module + ".domain")
                        .toList());
    }

    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreChecked_ThenNoneFormsACycle() {
        DomainPackageRules.domainPackagesAreFreeOfCycles(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenNullMarkedSubjectsAreListed_ThenTheKernelADomainAndEventsAreAmongThem() {
        assertThat(DomainPackageRules.nullMarkedSubjects(ROOT, ProductionClasses.ALL))
                .contains(ROOT + ".shared", ROOT + ".planning.domain", ROOT + ".planning.events");
    }

    @Test
    void GivenTheProductionClasses_WhenTheNullMarkedGuardChecks_ThenEveryKernelDomainAndEventsPackageCarriesIt() {
        assertThat(DomainPackageRules.packagesNotNullMarked(ROOT, ProductionClasses.ALL))
                .isEmpty();
    }
}
