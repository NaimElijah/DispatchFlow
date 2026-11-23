package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The shape rules of DL §3.5, §3.8, §7.3 and §13, and ADR-106's, checked against the production
 * classes. Each rule acts only on the types its predicate picks, so the guards below fail when a
 * predicate picks no production type, which would let its rule pass on nothing.
 */
class DomainShapeTest {

    private static final String ROOT = ProductionClasses.ROOT;

    private static Stream<String> picked(Predicate<JavaClass> predicate) {
        return ProductionClasses.ALL.stream().filter(predicate).map(JavaClass::getSimpleName);
    }

    @Test
    void GivenTheProductionClasses_WhenEventsArePicked_ThenTheModulesEventsAreAmongThem() {
        assertThat(picked(DomainShapeRules::isEvent)).contains("OrderCancelled", "PlanPublished", "OutcomeRecorded");
    }

    @Test
    void GivenTheProductionClasses_WhenPersonalValuesArePicked_ThenTheKernelsAndAModulesAreAmongThem() {
        assertThat(picked(DomainShapeRules::isPersonalValue)).contains("PhoneNumber", "GeoPoint", "EmailAddress");
    }

    @Test
    void GivenTheProductionClasses_WhenRestrictedTypesArePicked_ThenAggregatesSnapshotsAndResultsAreAmongThem() {
        assertThat(picked(DomainShapeRules::isRestricted))
                .contains(
                        "Plan",
                        "Order",
                        "Delivery",
                        "ActiveAdmins",
                        "DateCapacity",
                        "LaneCapacity",
                        "ValidationReport",
                        "Evaluation",
                        "ValidatedSolution");
    }

    @Test
    void GivenTheProductionClasses_WhenRestrictedConstructorsAreChecked_ThenAllArePrivate() {
        DomainShapeRules.restrictedConstructorsArePrivate(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenAggregateConstructionIsChecked_ThenOnlyEachRootBuildsItself() {
        DomainShapeRules.aggregatesAreBuiltOnlyByTheirRoot(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenAggregateRootsAreChecked_ThenNoneIsExtended() {
        DomainShapeRules.aggregateRootsAreNotExtended(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenEventsAreLocated_ThenEachLivesInAnEventsPackage() {
        DomainShapeRules.eventsLiveInEventsPackages(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenEventFieldsAreChecked_ThenEachHasAnAllowedType() {
        DomainShapeRules.eventFieldsAreClosed(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenErrorParametersAreChecked_ThenNoneCarriesPersonalData() {
        DomainShapeRules.errorParametersCarryNoPersonalData(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenPersonalValuesAreChecked_ThenEachDeclaresItsOwnToString() {
        DomainShapeRules.personalValuesPrintRedacted(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenErrorInterfacesAreChecked_ThenEachIsSealed() {
        DomainShapeRules.errorInterfacesAreSealed(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenErrorCodesAreChecked_ThenNoneIsDeclaredByHand() {
        DomainShapeRules.errorCodesAreDerived(ROOT).check(ProductionClasses.ALL);
    }
}
