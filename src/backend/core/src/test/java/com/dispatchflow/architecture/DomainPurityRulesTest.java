package com.dispatchflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.archfixture.alpha.adapter.out.persistence.RestoresInPersistence;
import com.dispatchflow.archfixture.alpha.application.AlphaUseCase;
import com.dispatchflow.archfixture.alpha.application.ReadsUnderLockOutsidePersistence;
import com.dispatchflow.archfixture.alpha.application.RestoresANonAggregate;
import com.dispatchflow.archfixture.alpha.application.RestoresByReferenceOutsidePersistence;
import com.dispatchflow.archfixture.alpha.application.RestoresOutsidePersistence;
import com.dispatchflow.archfixture.alpha.domain.AlphaAggregate;
import com.dispatchflow.archfixture.alpha.domain.AlphaCapacity;
import com.dispatchflow.archfixture.alpha.domain.AlphaValue;
import com.dispatchflow.archfixture.alpha.domain.AnnotatedWithModulith;
import com.dispatchflow.archfixture.alpha.domain.CallsOverHttp;
import com.dispatchflow.archfixture.alpha.domain.CatchesAnIllegalArgument;
import com.dispatchflow.archfixture.alpha.domain.CreatesANumberFormatException;
import com.dispatchflow.archfixture.alpha.domain.CreatesAnIllegalArgument;
import com.dispatchflow.archfixture.alpha.domain.CreatesAnIllegalState;
import com.dispatchflow.archfixture.alpha.domain.CreatesAnIllegalStateByReference;
import com.dispatchflow.archfixture.alpha.domain.DependsOnAFramework;
import com.dispatchflow.archfixture.alpha.domain.DependsOnAnotherModulesDomain;
import com.dispatchflow.archfixture.alpha.domain.DependsOnAnotherModulesEvents;
import com.dispatchflow.archfixture.alpha.domain.DependsOnItsApplication;
import com.dispatchflow.archfixture.alpha.domain.GeneratesARandomUuid;
import com.dispatchflow.archfixture.alpha.domain.GeneratesARandomUuidByReference;
import com.dispatchflow.archfixture.alpha.domain.HoldsAClock;
import com.dispatchflow.archfixture.alpha.domain.LogsWithJul;
import com.dispatchflow.archfixture.alpha.domain.LogsWithSlf4j;
import com.dispatchflow.archfixture.alpha.domain.LogsWithTheSystemLogger;
import com.dispatchflow.archfixture.alpha.domain.OpensASocket;
import com.dispatchflow.archfixture.alpha.domain.PrintsToStandardOut;
import com.dispatchflow.archfixture.alpha.domain.ReadsAFile;
import com.dispatchflow.archfixture.alpha.domain.ReadsAPath;
import com.dispatchflow.archfixture.alpha.domain.ReadsCurrentTimeMillis;
import com.dispatchflow.archfixture.alpha.domain.ReadsInstantNow;
import com.dispatchflow.archfixture.alpha.domain.ReadsInstantNowByReference;
import com.dispatchflow.archfixture.alpha.domain.ReadsLocalDateNow;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheDateFromAChronology;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheDefaultTimeZone;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheDefaultZone;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheDefaultZoneThroughZoneOffset;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheEnvironment;
import com.dispatchflow.archfixture.alpha.domain.ReadsTheSystemClock;
import com.dispatchflow.archfixture.alpha.domain.ShufflesAList;
import com.dispatchflow.archfixture.alpha.domain.StartsAProcess;
import com.dispatchflow.archfixture.alpha.domain.UsesACalendar;
import com.dispatchflow.archfixture.alpha.domain.UsesALegacyDate;
import com.dispatchflow.archfixture.alpha.domain.UsesARandomGenerator;
import com.dispatchflow.archfixture.alpha.domain.UsesMathRandom;
import com.dispatchflow.archfixture.alpha.domain.UsesRandom;
import com.dispatchflow.archfixture.alpha.domain.UsesThreadLocalRandom;
import com.dispatchflow.archfixture.alpha.events.AlphaEventKind;
import com.dispatchflow.archfixture.alpha.events.AlphaHappened;
import com.dispatchflow.archfixture.alpha.events.EventCreatesAnIllegalState;
import com.dispatchflow.archfixture.alpha.events.EventDependsOnAnotherModulesEvents;
import com.dispatchflow.archfixture.alpha.events.EventDependsOnItsDomain;
import com.dispatchflow.archfixture.alpha.events.EventDependsOnLibphonenumber;
import com.dispatchflow.archfixture.beta.application.BuildsARecordEvaluation;
import com.dispatchflow.archfixture.modulith.NamedInterface;
import com.dispatchflow.archfixture.planning.adapter.out.optimizer.OptimizerAdapter;
import com.dispatchflow.archfixture.planning.application.BuildsAShapedEvaluation;
import com.dispatchflow.archfixture.planning.application.BuildsAValidationReport;
import com.dispatchflow.archfixture.planning.application.BuildsAnEvaluation;
import com.dispatchflow.archfixture.planning.application.BuildsThroughTheNestedBuilder;
import com.dispatchflow.archfixture.planning.application.WrapsAValidationReport;
import com.dispatchflow.archfixture.planning.domain.BuildsAReportInTheDomain;
import com.dispatchflow.archfixture.planning.domain.Evaluation;
import com.dispatchflow.archfixture.planning.domain.ValidationReport;
import com.dispatchflow.archfixture.shared.KernelCreatesAnIllegalArgument;
import com.dispatchflow.archfixture.shared.KernelDependsOnAModule;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Proves each purity rule fires: every fixture is imported alone and evaluated against the rule
 * it breaks, so a rule that silently matches nothing fails here. The clean fixtures prove the
 * rules allow what DL §3.9 allows; one that lies outside its rule's subject is evaluated beside
 * {@link AlphaUseCase}, a clean class inside it, because the empty-rule check refuses a rule that
 * checks nothing.
 */
class DomainPurityRulesTest {

    private static final String FIXTURE_ROOT = "com.dispatchflow.archfixture";
    private static final String FIXTURE_MODULE_ANNOTATIONS = NamedInterface.class.getPackageName();

    private static boolean flags(ArchRule rule, Class<?>... fixtures) {
        JavaClasses imported = new ClassFileImporter().importClasses(fixtures);
        return rule.evaluate(imported).hasViolation();
    }

    private static ArchRule rule1() {
        return DomainPurityRules.onlyAllowedDependencies(FIXTURE_ROOT, FIXTURE_MODULE_ANNOTATIONS);
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                DependsOnAnotherModulesDomain.class,
                DependsOnAnotherModulesEvents.class,
                DependsOnItsApplication.class,
                DependsOnAFramework.class,
                EventDependsOnItsDomain.class,
                EventDependsOnAnotherModulesEvents.class,
                EventDependsOnLibphonenumber.class,
                KernelDependsOnAModule.class,
                AnnotatedWithModulith.class
            })
    void GivenAForbiddenDependency_WhenRule1Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(rule1(), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaValue.class, AlphaEventKind.class, AlphaHappened.class, KernelValue.class})
    void GivenOnlyAllowedDependencies_WhenRule1Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(rule1(), fixture)).isFalse();
    }

    @Test
    void GivenModuleAnnotationsOnPackageInfos_WhenRule1Evaluates_ThenNothingIsFlagged() {
        JavaClasses gamma = new ClassFileImporter().importPackages(FIXTURE_ROOT + ".gamma");

        assertThat(gamma.stream().map(type -> type.getSimpleName())).contains("package-info");
        assertThat(rule1().evaluate(gamma).hasViolation()).isFalse();
    }

    @Test
    void GivenAnotherFrameworksAnnotationOnADomainPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses delta = new ClassFileImporter().importPackages(FIXTURE_ROOT + ".delta");

        assertThat(delta.stream().map(type -> type.getSimpleName())).containsExactly("package-info");
        assertThat(rule1().evaluate(delta).hasViolation()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                ReadsInstantNow.class,
                ReadsInstantNowByReference.class,
                ReadsLocalDateNow.class,
                ReadsTheSystemClock.class,
                ReadsCurrentTimeMillis.class,
                UsesALegacyDate.class,
                UsesACalendar.class,
                ReadsTheDefaultZone.class,
                ReadsTheDefaultTimeZone.class,
                ReadsTheDefaultZoneThroughZoneOffset.class,
                ReadsTheDateFromAChronology.class,
                HoldsAClock.class
            })
    void GivenAClockRead_WhenRule2Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noSystemClock(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaValue.class, AlphaAggregate.class})
    void GivenNoClock_WhenRule2Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noSystemClock(FIXTURE_ROOT), fixture))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {GeneratesARandomUuid.class, GeneratesARandomUuidByReference.class})
    void GivenAGeneratedId_WhenRule3Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noIdGeneration(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                UsesRandom.class,
                UsesMathRandom.class,
                UsesThreadLocalRandom.class,
                UsesARandomGenerator.class,
                ShufflesAList.class
            })
    void GivenRandomness_WhenRule4Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noRandomness(FIXTURE_ROOT), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                ReadsAFile.class,
                ReadsAPath.class,
                CallsOverHttp.class,
                OpensASocket.class,
                PrintsToStandardOut.class,
                ReadsTheEnvironment.class,
                StartsAProcess.class
            })
    void GivenIo_WhenRule4Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noIo(FIXTURE_ROOT), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaValue.class, AlphaAggregate.class})
    void GivenIdnOrNoIoAtAll_WhenRule4Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noIo(FIXTURE_ROOT), fixture)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(classes = {LogsWithJul.class, LogsWithSlf4j.class, LogsWithTheSystemLogger.class})
    void GivenLogging_WhenRule4Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noLogging(FIXTURE_ROOT), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                CreatesAnIllegalArgument.class,
                CreatesAnIllegalState.class,
                CreatesAnIllegalStateByReference.class,
                CreatesANumberFormatException.class,
                KernelCreatesAnIllegalArgument.class,
                EventCreatesAnIllegalState.class
            })
    void GivenAGenericException_WhenRule5Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noGenericExceptions(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @Test
    void GivenAGenericExceptionCaughtButNotCreated_WhenRule5Evaluates_ThenNothingIsFlagged() {
        assertThat(flags(DomainPurityRules.noGenericExceptions(FIXTURE_ROOT), CatchesAnIllegalArgument.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                RestoresOutsidePersistence.class,
                RestoresByReferenceOutsidePersistence.class,
                ReadsUnderLockOutsidePersistence.class
            })
    void GivenARestoreOutsidePersistence_WhenRule6Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.restoreOnlyFromPersistence(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                RestoresInPersistence.class,
                RestoresANonAggregate.class,
                AlphaAggregate.class,
                AlphaCapacity.class
            })
    void GivenARestoreInPersistenceOrByTheTypeItself_WhenRule6Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.restoreOnlyFromPersistence(FIXTURE_ROOT), fixture, AlphaUseCase.class))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                BuildsAValidationReport.class,
                BuildsAnEvaluation.class,
                BuildsAShapedEvaluation.class,
                BuildsAReportInTheDomain.class,
                BuildsThroughTheNestedBuilder.class
            })
    void GivenAnOptimizerResultBuiltOutsideTheOptimizerAdapter_WhenEvaluated_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.optimizerResultsOnlyFromOptimizerAdapter(FIXTURE_ROOT), fixture))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                OptimizerAdapter.class,
                ValidationReport.class,
                Evaluation.class,
                Evaluation.Builder.class,
                BuildsARecordEvaluation.class,
                WrapsAValidationReport.class
            })
    void GivenTheOptimizerAdapterTheTypeItselfOrAnotherModulesType_WhenEvaluated_ThenNothingIsFlagged(
            Class<?> fixture) {
        assertThat(flags(
                        DomainPurityRules.optimizerResultsOnlyFromOptimizerAdapter(FIXTURE_ROOT),
                        fixture,
                        AlphaUseCase.class))
                .isFalse();
    }
}
