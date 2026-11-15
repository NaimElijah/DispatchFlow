package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The application rules, checked against the production classes. The guards below fail when the
 * module graph and the modules in the tree drift apart, or when a rule's subject picks no
 * production type, which would let the rule pass on nothing.
 */
class ApplicationTest {

    private static final String ROOT = ProductionClasses.ROOT;

    private static Set<String> topLevelPackages() {
        return ProductionClasses.ALL.stream()
                .map(JavaClass::getPackageName)
                .filter(name -> name.startsWith(ROOT + "."))
                .map(name -> name.substring(ROOT.length() + 1).split("\\.")[0])
                .collect(Collectors.toSet());
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
    void GivenTheProductionClasses_WhenRule4IsCheckedForSpis_ThenEachHasOneImplementationOutsideItsModule() {
        ApplicationRules.eachSpiHasOneImplementationOutsideItsModule(ROOT).check(ProductionClasses.ALL);
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
}
