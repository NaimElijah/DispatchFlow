package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;
import com.dispatchflow.archfixture.alpha.domain.AlphaError;
import com.dispatchflow.archfixture.alpha.domain.AlphaRecord;
import com.dispatchflow.archfixture.alpha.domain.error.CleanError;
import com.dispatchflow.archfixture.alpha.domain.error.ErrorWithALinkedCode;
import com.dispatchflow.archfixture.alpha.domain.error.ErrorWithARuleCode;
import com.dispatchflow.archfixture.alpha.domain.error.ErrorWithItsOwnCode;
import com.dispatchflow.archfixture.alpha.domain.error.OpenError;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAContact;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAContactName;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAListOfContactNames;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithALocation;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithANormalizedAddress;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAPhone;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithARecipient;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAnEmailAddress;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithAnOptionalListOfContactNames;
import com.dispatchflow.archfixture.alpha.domain.error.personalvalue.ErrorWithRecipients;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorInheritingAText;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithAListOfText;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithAStringBuilder;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithAText;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithAnArray;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithAnObject;
import com.dispatchflow.archfixture.alpha.domain.error.text.ErrorWithPlainText;
import com.dispatchflow.archfixture.alpha.domain.personal.ContactName;
import com.dispatchflow.archfixture.alpha.domain.personal.UnredactedContactName;
import com.dispatchflow.archfixture.alpha.domain.personal.UnredactedNote;
import com.dispatchflow.archfixture.alpha.domain.shape.AggregateWithANestedVariant;
import com.dispatchflow.archfixture.alpha.domain.shape.AggregateWithAProtectedConstructorTakingState;
import com.dispatchflow.archfixture.alpha.domain.shape.AggregateWithAPublicConstructor;
import com.dispatchflow.archfixture.alpha.domain.shape.AggregateWithHibernatesConstructor;
import com.dispatchflow.archfixture.alpha.domain.shape.BuildsAnAggregateOutsideItsRoot;
import com.dispatchflow.archfixture.alpha.domain.shape.EventOutsideEvents;
import com.dispatchflow.archfixture.alpha.domain.shape.ExtendsAnAggregate;
import com.dispatchflow.archfixture.alpha.domain.shape.ReferencesAnAggregatesConstructor;
import com.dispatchflow.archfixture.alpha.domain.shape.SnapshotRecordWithReadUnderLock;
import com.dispatchflow.archfixture.alpha.events.CleanEvent;
import com.dispatchflow.archfixture.alpha.events.EventInheritingAText;
import com.dispatchflow.archfixture.alpha.events.EventWithABoxedFlag;
import com.dispatchflow.archfixture.alpha.events.EventWithABoxedNumber;
import com.dispatchflow.archfixture.alpha.events.EventWithABoxedTotal;
import com.dispatchflow.archfixture.alpha.events.EventWithAContactName;
import com.dispatchflow.archfixture.alpha.events.EventWithAListOfText;
import com.dispatchflow.archfixture.alpha.events.EventWithALocation;
import com.dispatchflow.archfixture.alpha.events.EventWithAPersonalEnum;
import com.dispatchflow.archfixture.alpha.events.EventWithAPhone;
import com.dispatchflow.archfixture.alpha.events.EventWithAPhoneArray;
import com.dispatchflow.archfixture.alpha.events.EventWithARuleViolation;
import com.dispatchflow.archfixture.alpha.events.EventWithAText;
import com.dispatchflow.archfixture.alpha.events.EventWithAnIdArray;
import com.dispatchflow.archfixture.alpha.events.EventWithAnotherPackagesEnum;
import com.dispatchflow.archfixture.alpha.events.EventWithFreshIds;
import com.dispatchflow.archfixture.alpha.events.Reported;
import com.dispatchflow.archfixture.planning.domain.Evaluation;
import com.dispatchflow.archfixture.planning.domain.ValidatedSolution;
import com.dispatchflow.archfixture.planning.domain.ValidationReport;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Proves each shape rule fires on the fixture that breaks it and allows the clean ones. */
class DomainShapeRulesTest {

    private static final String FIXTURE_ROOT = FixtureClasses.ROOT;

    private static boolean flags(ArchRule rule, Class<?>... fixtures) {
        return rule.evaluate(FixtureClasses.only(fixtures)).hasViolation();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                SnapshotRecordWithReadUnderLock.class,
                AggregateWithAPublicConstructor.class,
                AggregateWithAProtectedConstructorTakingState.class,
                com.dispatchflow.archfixture.planning.domain.shaped.Evaluation.class,
                ValidationReport.class,
                Evaluation.Builder.class,
                ValidatedSolution.class
            })
    void GivenARestrictedTypeWithAVisibleConstructor_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.restrictedConstructorsArePrivate(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                AlphaAggregate.class,
                AggregateWithHibernatesConstructor.class,
                AlphaCapacity.class,
                Evaluation.class,
                AlphaRecord.class,
                com.dispatchflow.archfixture.beta.domain.Evaluation.class
            })
    void GivenPrivateConstructorsOrAnUnrestrictedType_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.restrictedConstructorsArePrivate(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                BuildsAnAggregateOutsideItsRoot.class,
                ReferencesAnAggregatesConstructor.class,
                ExtendsAnAggregate.class
            })
    void GivenAnAggregateBuiltOutsideItsRoot_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.aggregatesAreBuiltOnlyByTheirRoot(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                AlphaAggregate.class,
                AggregateWithHibernatesConstructor.class,
                AggregateWithANestedVariant.Variant.class,
                AlphaRecord.class
            })
    void GivenAnAggregateBuiltWithinItsRootOrNoAggregate_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.aggregatesAreBuiltOnlyByTheirRoot(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {ExtendsAnAggregate.class, AggregateWithANestedVariant.Variant.class})
    void GivenASubclassOfAConcreteRoot_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.aggregateRootsAreNotExtended(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                AlphaAggregate.class,
                AggregateWithHibernatesConstructor.class,
                AggregateWithANestedVariant.class,
                AlphaRecord.class
            })
    void GivenARootThatExtendsAggregateRootOrNoAggregate_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.aggregateRootsAreNotExtended(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {EventOutsideEvents.class})
    void GivenAnEventOutsideAnEventsPackage_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.eventsLiveInEventsPackages(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {CleanEvent.class, Reported.class, CleanError.class})
    void GivenAnEventInItsEventsPackageOrNoEvent_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.eventsLiveInEventsPackages(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                EventWithAText.class,
                EventWithAPhone.class,
                EventWithALocation.class,
                EventWithAListOfText.class,
                EventWithAnotherPackagesEnum.class,
                EventWithAPhoneArray.class,
                EventWithAnIdArray.class,
                EventWithFreshIds.class,
                EventWithARuleViolation.class,
                EventInheritingAText.class,
                EventWithAContactName.class,
                EventWithAPersonalEnum.class,
                EventWithABoxedNumber.class,
                EventWithABoxedTotal.class,
                EventWithABoxedFlag.class
            })
    void GivenAnEventFieldOfAForbiddenType_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.eventFieldsAreClosed(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {CleanEvent.class, Reported.class})
    void GivenAnEventWithOnlyAllowedFields_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.eventFieldsAreClosed(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                ErrorWithAText.class,
                ErrorWithAPhone.class,
                ErrorWithALocation.class,
                ErrorWithAListOfText.class,
                ErrorWithAStringBuilder.class,
                ErrorWithAnObject.class,
                ErrorWithAnArray.class,
                ErrorWithAnEmailAddress.class,
                ErrorWithANormalizedAddress.class,
                ErrorInheritingAText.class,
                ErrorWithAContactName.class,
                ErrorWithAListOfContactNames.class,
                ErrorWithPlainText.class,
                ErrorWithAnOptionalListOfContactNames.class
            })
    void GivenAnErrorParameterThatCouldCarryPersonalData_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {CleanError.class, CleanEvent.class, ErrorWithARuleCode.class})
    void GivenAnErrorWithAllowedParametersOrNoError_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    /**
     * The rule looks inside field types. The one fixture import holds those types fully, as the
     * production check's whole-root import does, so the error alone is evaluated.
     */
    @Test
    void GivenPersonalDataInsideAnUnmarkedWrapper_WhenEvaluated_ThenTheErrorIsFlagged() {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), ErrorWithAContact.class))
                .isTrue();
    }

    @Test
    void GivenPersonalValuesInAnArrayInsideAWrapper_WhenEvaluated_ThenTheErrorIsFlagged() {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), ErrorWithRecipients.class))
                .isTrue();
    }

    @Test
    void GivenASealedTypeWhoseVariantHoldsAPersonalValue_WhenEvaluated_ThenTheErrorIsFlagged() {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), ErrorWithARecipient.class))
                .isTrue();
    }

    @Test
    void GivenAValueThatRefersToItsOwnType_WhenEvaluated_ThenTheSearchEndsAndNothingIsFlagged() {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), ErrorWithALinkedCode.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {UnredactedContactName.class, UnredactedNote.class})
    void GivenAPersonalValueWithoutItsOwnToString_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.personalValuesPrintRedacted(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {ContactName.class, CleanError.class})
    void GivenAPersonalValueWithItsOwnToStringOrNoPersonalValue_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.personalValuesPrintRedacted(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {OpenError.class})
    void GivenAnErrorInterfaceThatIsNotSealed_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorInterfacesAreSealed(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaError.class, CleanError.class})
    void GivenASealedErrorInterfaceOrARecord_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorInterfacesAreSealed(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {ErrorWithItsOwnCode.class})
    void GivenAnErrorThatDeclaresItsOwnCode_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorCodesAreDerived(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {CleanError.class, ErrorWithAText.class})
    void GivenAnErrorWithADerivedCode_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorCodesAreDerived(FIXTURE_ROOT), fixture))
                .isFalse();
    }
}
