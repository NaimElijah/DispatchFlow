package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.adapter.out.persistence.ListCrudRepositoryOfRoots;
import com.dispatchflow.archfixture.alpha.adapter.out.persistence.NarrowRepository;
import com.dispatchflow.archfixture.alpha.adapter.out.persistence.ReadsAnotherModulesEntity;
import com.dispatchflow.archfixture.alpha.adapter.out.persistence.SpecifiesItsWayAround;
import com.dispatchflow.archfixture.alpha.adapter.out.persistence.WideRepository;
import com.dispatchflow.archfixture.alpha.domain.entity.CollectsWithoutASchema;
import com.dispatchflow.archfixture.alpha.domain.entity.EmbedsAnEnumByPosition;
import com.dispatchflow.archfixture.alpha.domain.entity.GeneratesItsKey;
import com.dispatchflow.archfixture.alpha.domain.entity.JoinsThroughADefaultTable;
import com.dispatchflow.archfixture.alpha.domain.entity.KeysAMapByPosition;
import com.dispatchflow.archfixture.alpha.domain.entity.MappedChild;
import com.dispatchflow.archfixture.alpha.domain.entity.MappedInAnotherSchema;
import com.dispatchflow.archfixture.alpha.domain.entity.MappedRoot;
import com.dispatchflow.archfixture.alpha.domain.entity.MappedWithoutASchema;
import com.dispatchflow.archfixture.alpha.domain.entity.RelatesToAnotherAggregate;
import com.dispatchflow.archfixture.alpha.domain.entity.RelatesToAnotherModule;
import com.dispatchflow.archfixture.alpha.domain.entity.RootWithoutALoadCheck;
import com.dispatchflow.archfixture.alpha.domain.entity.RootsRelatingToEachOtherFirst;
import com.dispatchflow.archfixture.alpha.domain.entity.RootsRelatingToEachOtherSecond;
import com.dispatchflow.archfixture.alpha.domain.entity.SharesAnotherAggregatesChild;
import com.dispatchflow.archfixture.alpha.domain.entity.StoresAnEnumAsAnOrdinal;
import com.dispatchflow.archfixture.alpha.domain.entity.StoresAnEnumByPosition;
import com.dispatchflow.archfixture.alpha.domain.entity.StoresEnumsByPositionInAList;
import com.dispatchflow.archfixture.alpha.domain.entity.VersionedChild;
import com.dispatchflow.archfixture.alpha.domain.entity.VersionsThroughAGetter;
import com.dispatchflow.archfixture.alpha.domain.mapping.MappedWithJakartaPersistence;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each rule of {@link PersistenceRules} flags its deliberately wrong fixture and passes the clean ones. */
class PersistenceRulesTest {

    private static final String FIXTURE_ROOT = FixtureClasses.ROOT;

    private static boolean flags(ArchRule rule, Class<?>... fixtures) {
        return rule.evaluate(FixtureClasses.only(fixtures)).hasViolation();
    }

    @ParameterizedTest
    @ValueSource(classes = {RelatesToAnotherAggregate.class, RelatesToAnotherModule.class})
    void GivenARelationshipOutsideItsAggregate_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(PersistenceRules.relationshipsStayInsideTheAggregate(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenTwoRootsThatMapEachOther_WhenChecked_ThenBothSidesAreFlagged() {
        assertThat(PersistenceRules.relationshipsStayInsideTheAggregate(FIXTURE_ROOT)
                        .evaluate(FixtureClasses.only(
                                RootsRelatingToEachOtherFirst.class, RootsRelatingToEachOtherSecond.class))
                        .getFailureReport()
                        .getDetails())
                .hasSize(2);
    }

    @Test
    void GivenARootThatRelatesToAnotherAggregatesChild_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        PersistenceRules.relationshipsStayInsideTheAggregate(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedChild.class,
                        SharesAnotherAggregatesChild.class))
                .isTrue();
    }

    @Test
    void GivenARootWithItsChildrenAndAChildReferringBackToIt_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        PersistenceRules.relationshipsStayInsideTheAggregate(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedChild.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {VersionedChild.class, VersionsThroughAGetter.class})
    void GivenAVersionOutsideAggregateRoot_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(PersistenceRules.onlyTheAggregateRootIsVersioned(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenARootThatInheritsAggregateRootsVersion_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        PersistenceRules.onlyTheAggregateRootIsVersioned(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedChild.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                StoresAnEnumByPosition.class,
                StoresAnEnumAsAnOrdinal.class,
                StoresEnumsByPositionInAList.class,
                EmbedsAnEnumByPosition.class,
                KeysAMapByPosition.class
            })
    void GivenAnEnumStoredByPosition_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(PersistenceRules.enumsAreStoredByName(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenEnumsStoredByNameAloneAndInAList_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        PersistenceRules.enumsAreStoredByName(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedWithJakartaPersistence.class))
                .isFalse();
    }

    @Test
    void GivenAnEntityRootWithNoLoadCheck_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(PersistenceRules.everyEntityRootChecksWhatItLoads(FIXTURE_ROOT), RootWithoutALoadCheck.class))
                .isTrue();
    }

    @Test
    void GivenAnEntityRootWithItsLoadCheckAndAChildWithout_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        PersistenceRules.everyEntityRootChecksWhatItLoads(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedChild.class))
                .isFalse();
    }

    @Test
    void GivenAGeneratedKey_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(PersistenceRules.noKeyIsGenerated(FIXTURE_ROOT), GeneratesItsKey.class))
                .isTrue();
    }

    @Test
    void GivenKeysTheFactoriesAssign_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(PersistenceRules.noKeyIsGenerated(FIXTURE_ROOT), MappedRoot.class, MappedChild.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {WideRepository.class, ListCrudRepositoryOfRoots.class, SpecifiesItsWayAround.class})
    void GivenARepositoryThatOffersCrud_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(PersistenceRules.noRepositoryOffersMoreThanItsPortNeeds(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenAPlainRepository_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(PersistenceRules.noRepositoryOffersMoreThanItsPortNeeds(FIXTURE_ROOT), NarrowRepository.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                MappedInAnotherSchema.class,
                MappedWithoutASchema.class,
                CollectsWithoutASchema.class,
                JoinsThroughADefaultTable.class
            })
    void GivenAnEntityOutsideItsModulesSchema_WhenChecked_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(PersistenceRules.everyEntityLivesInItsModulesSchema(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenAnAdapterThatReadsAnotherModulesEntity_WhenChecked_ThenItIsFlagged() {
        assertThat(flags(
                        PersistenceRules.adaptersUseOnlyTheirModulesEntities(FIXTURE_ROOT),
                        ReadsAnotherModulesEntity.class))
                .isTrue();
    }

    @Test
    void GivenAnAdapterOverItsOwnModulesEntity_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(PersistenceRules.adaptersUseOnlyTheirModulesEntities(FIXTURE_ROOT), NarrowRepository.class))
                .isFalse();
    }

    @Test
    void GivenEntitiesInTheirModulesSchema_WhenChecked_ThenNothingIsFlagged() {
        assertThat(flags(
                        PersistenceRules.everyEntityLivesInItsModulesSchema(FIXTURE_ROOT),
                        MappedRoot.class,
                        MappedChild.class,
                        MappedWithJakartaPersistence.class))
                .isFalse();
    }
}
