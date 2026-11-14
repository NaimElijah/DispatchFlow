package com.dispatchflow.architecture;

import org.junit.jupiter.api.Test;

/** The shape rules of DL §3.5, §3.8, §7.3 and §13, checked against the production classes. */
class DomainShapeTest {

    private static final String ROOT = ProductionClasses.ROOT;

    @Test
    void GivenTheProductionClasses_WhenRestrictedConstructorsAreChecked_ThenAllArePrivate() {
        DomainShapeRules.restrictedConstructorsArePrivate(ROOT).check(ProductionClasses.ALL);
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
    void GivenTheProductionClasses_WhenErrorInterfacesAreChecked_ThenEachIsSealed() {
        DomainShapeRules.errorInterfacesAreSealed(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenErrorCodesAreChecked_ThenNoneIsDeclaredByHand() {
        DomainShapeRules.errorCodesAreDerived(ROOT).check(ProductionClasses.ALL);
    }
}
