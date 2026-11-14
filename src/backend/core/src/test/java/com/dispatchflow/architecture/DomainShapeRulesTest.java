package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.domain.AggregateWithAPublicConstructor;
import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;
import com.dispatchflow.archfixture.alpha.domain.AlphaError;
import com.dispatchflow.archfixture.alpha.domain.AlphaRecord;
import com.dispatchflow.archfixture.alpha.domain.CleanError;
import com.dispatchflow.archfixture.alpha.domain.Contact;
import com.dispatchflow.archfixture.alpha.domain.ContactName;
import com.dispatchflow.archfixture.alpha.domain.ErrorInheritingAText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAContact;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAContactName;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithALinkedCode;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAListOfContactNames;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAListOfText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithALocation;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithANormalizedAddress;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAPhone;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithARecipient;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithARuleCode;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAStringBuilder;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAnArray;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAnEmailAddress;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAnObject;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithAnOptionalListOfContactNames;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithItsOwnCode;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithPlainText;
import com.dispatchflow.archfixture.alpha.domain.ErrorWithRecipients;
import com.dispatchflow.archfixture.alpha.domain.EventOutsideEvents;
import com.dispatchflow.archfixture.alpha.domain.LinkedCode;
import com.dispatchflow.archfixture.alpha.domain.Name;
import com.dispatchflow.archfixture.alpha.domain.OpenError;
import com.dispatchflow.archfixture.alpha.domain.Recipient;
import com.dispatchflow.archfixture.alpha.domain.Recipients;
import com.dispatchflow.archfixture.alpha.domain.RuleCode;
import com.dispatchflow.archfixture.alpha.domain.SnapshotRecordWithReadUnderLock;
import com.dispatchflow.archfixture.alpha.domain.UnredactedContactName;
import com.dispatchflow.archfixture.alpha.domain.UnredactedNote;
import com.dispatchflow.archfixture.alpha.events.CleanEvent;
import com.dispatchflow.archfixture.alpha.events.EventInheritingAText;
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
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
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
                EventWithAPersonalEnum.class
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
     * The rule looks inside field types, so the types it looks into are imported with the error, as
     * the whole-root import of the production check imports them.
     */
    @Test
    void GivenPersonalDataInsideAnUnmarkedWrapper_WhenEvaluated_ThenTheErrorIsFlagged() {
        JavaClasses imported =
                new ClassFileImporter().importClasses(ErrorWithAContact.class, Contact.class, ContactName.class);

        assertThat(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT)
                        .evaluate(imported)
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenPersonalValuesInAnArrayInsideAWrapper_WhenEvaluated_ThenTheErrorIsFlagged() {
        JavaClasses imported =
                new ClassFileImporter().importClasses(ErrorWithRecipients.class, Recipients.class, Name.class);

        assertThat(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT)
                        .evaluate(imported)
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenASealedTypeWhoseVariantHoldsAPersonalValue_WhenEvaluated_ThenTheErrorIsFlagged() {
        JavaClasses imported = new ClassFileImporter()
                .importClasses(
                        ErrorWithARecipient.class,
                        Recipient.class,
                        Recipient.Named.class,
                        Recipient.Anonymous.class,
                        Name.class);

        assertThat(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT)
                        .evaluate(imported)
                        .hasViolation())
                .isTrue();
    }

    @Test
    void GivenAValueThatRefersToItsOwnType_WhenEvaluated_ThenTheSearchEndsAndNothingIsFlagged() {
        JavaClasses imported =
                new ClassFileImporter().importClasses(ErrorWithALinkedCode.class, LinkedCode.class, RuleCode.class);

        assertThat(DomainShapeRules.errorParametersCarryNoPersonalData(FIXTURE_ROOT)
                        .evaluate(imported)
                        .hasViolation())
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
