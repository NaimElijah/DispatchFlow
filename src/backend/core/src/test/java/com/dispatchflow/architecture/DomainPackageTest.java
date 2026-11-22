package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.library.dependencies.Slices;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The package rules of ADR-118, checked against the production classes. The guards below fail when
 * a rule's subject picks no production package, which would let the rule pass on nothing.
 */
class DomainPackageTest {

    private static final String ROOT = ProductionClasses.ROOT;
    // The tables of ADR-118 and ADR-125: these modules' domains are split; the others stay flat.
    private static final List<String> SPLIT_MODULES =
            List.of("access", "fleet", "liveops", "messaging", "planning", "reporting");
    private static final List<String> FLAT_MODULES = List.of("audit", "orders");
    private static final List<String> MODULES =
            Stream.concat(SPLIT_MODULES.stream(), FLAT_MODULES.stream()).toList();

    private static List<String> domainSlices() {
        return Slices.assignedFrom(DomainPackageRules.domainPackages(ROOT)).transform(ProductionClasses.ALL).stream()
                .map(slice -> slice.iterator().next().getPackageName())
                .toList();
    }

    private static Map<String, Long> slicesPerModule(List<String> modules) {
        List<String> slices = domainSlices();
        return modules.stream().collect(Collectors.toMap(Function.identity(), module -> {
            String domain = ROOT + "." + module + ".domain";
            return slices.stream()
                    .filter(slice -> slice.equals(domain) || slice.startsWith(domain + "."))
                    .count();
        }));
    }

    /** Every module's domain root is a slice of its own, split or flat. */
    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreSliced_ThenEachModulesDomainRootIsASlice() {
        assertThat(domainSlices())
                .containsAll(MODULES.stream()
                        .map(module -> ROOT + "." + module + ".domain")
                        .toList());
    }

    /**
     * A split module yields its root and at least one aggregate package, so the cycle rule and the
     * root rule have more than one package of it to compare.
     */
    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreSliced_ThenEachSplitModuleYieldsMoreThanOneSlice() {
        assertThat(slicesPerModule(SPLIT_MODULES))
                .as("domain slices per split module")
                .containsOnlyKeys(SPLIT_MODULES)
                .allSatisfy((module, count) ->
                        assertThat(count).as("domain slices of " + module).isGreaterThan(1L));
    }

    /** A flat module yields its domain root alone. */
    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreSliced_ThenEachFlatModuleYieldsExactlyOneSlice() {
        assertThat(slicesPerModule(FLAT_MODULES))
                .as("domain slices per flat module")
                .containsExactlyInAnyOrderEntriesOf(Map.of("audit", 1L, "orders", 1L));
    }

    @Test
    void GivenTheProductionClasses_WhenTheirDomainPackagesAreChecked_ThenNoneFormsACycle() {
        DomainPackageRules.domainPackagesAreFreeOfCycles(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheirDomainRootsAreChecked_ThenNoneUsesItsSubpackages() {
        DomainPackageRules.domainRootsUseNoSubpackage(ROOT).check(ProductionClasses.ALL);
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
