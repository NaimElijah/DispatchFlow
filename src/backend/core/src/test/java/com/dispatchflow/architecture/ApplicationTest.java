package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The application rules, checked against the production classes.
 *
 * <p>A rule whose subject picks no class can't pass on nothing: ArchUnit's {@code
 * archRule.failOnEmptyShould} is on by default, and no {@code archunit.properties} turns it off.
 * Rule 4 skips every type that is not an interface, though, so the api and spi guard checks that
 * its subjects hold the ports themselves, not only the records beside them. The other guards fail
 * when the module graph, the dashed arrows and the tree drift apart.
 */
class ApplicationTest {

    private static final String ROOT = ProductionClasses.ROOT;
    private static final String SPI_PACKAGE = ".application.spi";

    private static Set<String> topLevelPackages() {
        return ProductionClasses.ALL.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ROOT + "."))
                .map(name -> name.substring(ROOT.length() + 1).split("\\.")[0])
                .collect(Collectors.toSet());
    }

    /** The module that owns an spi interface named below the root: its first segment. */
    private static String ownerOf(String nameBelowTheRoot) {
        return nameBelowTheRoot.substring(0, nameBelowTheRoot.indexOf('.'));
    }

    private static Stream<String> interfacesIn(String layer) {
        return ProductionClasses.ALL.stream()
                .filter(type ->
                        type.getPackageName().matches(ROOT.replace(".", "\\.") + "\\.[^.]+\\.application\\." + layer))
                .filter(JavaClass::isInterface)
                .map(JavaClass::getSimpleName);
    }

    @Test
    void GivenTheProductionClasses_WhenTheirTopLevelPackagesAreListed_ThenTheyAreTheGraphsModulesTheKernelAndTheWeb() {
        assertThat(topLevelPackages())
                .containsExactlyInAnyOrderElementsOf(Stream.concat(
                                ApplicationRules.MODULE_GRAPH.keySet().stream(), ApplicationRules.NOT_MODULES.stream())
                        .toList());
    }

    @Test
    void GivenTheModuleGraph_WhenItsArrowsAreRead_ThenEachPointsAtAModuleOfTheGraph() {
        assertThat(ApplicationRules.MODULE_GRAPH.values().stream().flatMap(Set::stream))
                .allMatch(ApplicationRules.MODULE_GRAPH::containsKey);
    }

    @Test
    void GivenTheModuleGraph_WhenItsCyclesAreSought_ThenNoModuleReachesItself() {
        assertThat(ApplicationRules.modulesOnACycle(ApplicationRules.MODULE_GRAPH))
                .isEmpty();
    }

    @Test
    void GivenTheRenamedSpiImplementations_WhenTheirPortsAreLookedUp_ThenEachHasADashedArrow() {
        assertThat(ApplicationRules.SPI_IMPLEMENTERS)
                .containsKeys(ApplicationRules.SPI_IMPLEMENTATION_NAMES.keySet().toArray(String[]::new));
    }

    @Test
    void GivenTheDashedArrows_WhenTheirInterfacesAreLookedUp_ThenEachIsAProductionSpiInterface() {
        assertThat(ApplicationRules.SPI_IMPLEMENTERS.keySet()).allSatisfy(name -> {
            assertThat(ProductionClasses.ALL.contain(ROOT + "." + name))
                    .as("%s is a production class", name)
                    .isTrue();
            JavaClass port = ProductionClasses.ALL.get(ROOT + "." + name);
            assertThat(port.isInterface()).as("%s is an interface", name).isTrue();
            assertThat(port.getPackageName()).isEqualTo(ROOT + "." + ownerOf(name) + SPI_PACKAGE);
        });
    }

    @Test
    void GivenTheDashedArrows_WhenTheirModulesAreRead_ThenEachImplementerHasAnArrowToTheOwner() {
        assertThat(ApplicationRules.SPI_IMPLEMENTERS)
                .allSatisfy((name, implementer) -> assertThat(ApplicationRules.MODULE_GRAPH.get(implementer))
                        .as("the arrows of %s, which implements %s", implementer, name)
                        .contains(ownerOf(name)));
    }

    @Test
    void GivenTheProductionClasses_WhenApiAndSpiInterfacesArePicked_ThenEachLayersPortsAreAmongThem() {
        assertThat(interfacesIn("api")).contains("AuditLog", "Accounts", "PublishedRoutes");
        assertThat(interfacesIn("spi")).contains("RecordUsage", "OrderPlacement", "ExecutionFacts");
    }

    @Test
    void GivenTheProductionClasses_WhenRule1IsChecked_ThenTheApplicationDependsOnlyOnWhatIsAllowed() {
        ApplicationRules.onlyAllowedDependencies(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule2IsChecked_ThenModulesDependOnlyAlongTheGraph() {
        ApplicationRules.modulesDependAlongTheGraph(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule3IsChecked_ThenNoApiOrSpiUsesADomainType() {
        ApplicationRules.apiAndSpiUseNoDomainType(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule4IsCheckedForApis_ThenEachHasOneImplementationInItsModule() {
        ApplicationRules.eachApiHasOneImplementationInItsModule(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule4IsCheckedForSpis_ThenEachHasOneImplementationWhereItsArrowPoints() {
        ApplicationRules.eachSpiHasOneImplementationInItsArrowsModule(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenNullMarkedSubjectsAreListed_ThenTheSharedPortsAndAModulesLayersAreAmongThem() {
        assertThat(ApplicationRules.nullMarkedSubjects(ROOT, ProductionClasses.ALL))
                .contains(
                        ROOT + ".shared.application",
                        ROOT + ".audit.application.api",
                        ROOT + ".audit.application.port.out",
                        ROOT + ".audit.application.service",
                        ROOT + ".planning.application.spi");
    }

    @Test
    void GivenTheProductionClasses_WhenTheNullMarkedGuardChecks_ThenEveryApplicationPackageCarriesIt() {
        assertThat(ApplicationRules.packagesNotNullMarked(ROOT, ProductionClasses.ALL))
                .isEmpty();
    }

    @Test
    void GivenTheProductionClasses_WhenRule6IsChecked_ThenNoDomainEventOrKernelRootClassUsesTheSharedPorts() {
        ApplicationRules.domainReachesNoApplicationPort(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule7ChecksCycles_ThenTheApplicationPackagesAreFreeOfThem() {
        ApplicationRules.applicationPackagesAreFreeOfCycles(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule7ChecksEachApplicationPackage_ThenNoneUsesItsSubpackages() {
        ApplicationRules.applicationPackagesUseNoSubpackage(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule8IsChecked_ThenReportingUsesOnlyTheEventsOfTheModulesItReports() {
        ApplicationRules.reportingHearsOnlyTheEventsOfItsSources(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule9IsChecked_ThenNoAdapterUsesAnotherAdapterOfItsModule() {
        ApplicationRules.adaptersOfAModuleStayApart(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule10IsChecked_ThenNoAdapterUsesAnotherModulesAdapter() {
        ApplicationRules.adaptersUseNoOtherModulesAdapter(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRule11IsChecked_ThenNoSharedAdapterUsesAModule() {
        ApplicationRules.sharedAdaptersUseNoModule(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenTheirAdaptersAreListed_ThenTheyAreExactlyTheDeclaredOnes() {
        assertThat(ApplicationRules.adaptersByTopLevelPackage(ROOT, ProductionClasses.ALL))
                .isEqualTo(ApplicationRules.DECLARED_ADAPTERS);
    }

    @Test
    void GivenTheProductionClasses_WhenTheAdapterNullMarkedGuardChecks_ThenEveryAdapterPackageCarriesIt() {
        assertThat(ApplicationRules.adapterPackagesNotNullMarked(ROOT, ProductionClasses.ALL))
                .isEmpty();
    }
}
