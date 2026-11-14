package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.domain.AggregateWithAPublicConstructor;
import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;
import com.dispatchflow.archfixture.alpha.domain.AlphaRecord;
import com.dispatchflow.archfixture.alpha.domain.CleanError;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAListOfText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithALocation;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAPhone;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithItsOwnCode;
import com.dispatchflow.archfixture.alpha.domain.EventOutsideEvents;
import com.dispatchflow.archfixture.alpha.domain.SnapshotRecordWithReadUnderLock;
import com.dispatchflow.archfixture.alpha.events.CleanEvent;
import com.dispatchflow.archfixture.alpha.events.EventWithAListOfText;
import com.dispatchflow.archfixture.alpha.events.EventWithALocation;
import com.dispatchflow.archfixture.alpha.events.EventWithAPhone;
import com.dispatchflow.archfixture.alpha.events.EventWithAText;
import com.dispatchflow.archfixture.alpha.events.EventWithAnotherPackagesEnum;
import com.dispatchflow.archfixture.alpha.events.Reported;
import com.dispatchflow.archfixture.planning.domain.Evaluation;
import com.dispatchflow.archfixture.planning.domain.ValidationReport;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Proves each shape rule fires on the fixture that breaks it and allows the clean ones. */
class DomainShapeRulesTest {

    private static final String FIXTURE_ROOT = "com.dispatchflow.archfixture";

    private static boolean flags(ArchRule rule, Class<?> fixture) {
        JavaClasses imported = new ClassFileImporter().importClasses(fixture);
        return rule.evaluate(imported).hasViolation();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                SnapshotRecordWithReadUnderLock.class,
                AggregateWithAPublicConstructor.class,
                com.dispatchflow.archfixture.beta.domain.Evaluation.class,
                ValidationReport.class
            })
    void GivenARestrictedTypeWithAVisibleConstructor_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.restrictedConstructorsArePrivate(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaAggregate.class, AlphaCapacity.class, Evaluation.class, AlphaRecord.class})
    void GivenPrivateConstructorsOrAnUnrestrictedType_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.restrictedConstructorsArePrivate(FIXTURE_ROOT), fixture))
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
                EventWithAnotherPackagesEnum.class
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
            classes = {ErrorWithAText.class, ErrorWithAPhone.class, ErrorWithALocation.class, ErrorWithAListOfText.class
            })
    void GivenAnErrorParameterThatCouldCarryPersonalData_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {CleanError.class, CleanEvent.class})
    void GivenAnErrorWithAllowedParametersOrNoError_WhenEvaluated_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT), fixture))
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
