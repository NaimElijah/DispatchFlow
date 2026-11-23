package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import jakarta.persistence.Embeddable;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;

/**
 * The JPA mapping rules of ADR-126, checked against the production classes. Until a lane maps its
 * aggregate, the kernel's {@code AggregateRoot} and embeddable values are all the mapping there is,
 * and the guard below fails if those vanish, which would let the rules pass on nothing mapped.
 */
class PersistenceTest {

    private static final String ROOT = ProductionClasses.ROOT;

    @Test
    void GivenTheProductionClasses_WhenTheMappedKernelTypesArePicked_ThenTheRootAndTheEmbeddablesAreAmongThem() {
        assertThat(ProductionClasses.ALL.stream()
                        .filter(type ->
                                type.isAnnotatedWith(MappedSuperclass.class) || type.isAnnotatedWith(Embeddable.class))
                        .map(JavaClass::getSimpleName))
                .contains("AggregateRoot", "TimeWindow", "GeoPoint", "DaySeconds");
    }

    @Test
    void GivenTheProductionClasses_WhenRelationshipsAreChecked_ThenEachStaysInsideItsAggregate() {
        PersistenceRules.relationshipsStayInsideTheAggregate(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenVersionsAreChecked_ThenOnlyAggregateRootDeclaresOne() {
        PersistenceRules.onlyTheAggregateRootIsVersioned(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenStoredEnumsAreChecked_ThenEachIsStoredByName() {
        PersistenceRules.enumsAreStoredByName(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenEntityRootsAreChecked_ThenEachChecksWhatItLoads() {
        PersistenceRules.everyEntityRootChecksWhatItLoads(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenKeysAreChecked_ThenNoneIsGenerated() {
        PersistenceRules.noKeyIsGenerated(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenRepositoriesAreChecked_ThenNoneOffersMoreThanItsPortNeeds() {
        PersistenceRules.noRepositoryOffersMoreThanItsPortNeeds(ROOT).check(ProductionClasses.ALL);
    }

    @Test
    void GivenTheProductionClasses_WhenEntitiesAreChecked_ThenEachLivesInItsModulesSchema() {
        PersistenceRules.everyEntityLivesInItsModulesSchema(ROOT).check(ProductionClasses.ALL);
    }
}
