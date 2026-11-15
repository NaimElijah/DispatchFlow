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
import com.dispatchflow.archfixture.alpha.domain.clock.HoldsAClock;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsCurrentTimeMillis;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsInstantNow;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsInstantNowByReference;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsLocalDateNow;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsNanoTime;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsTheDateFromAChronology;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsTheDefaultTimeZone;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsTheDefaultZone;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsTheDefaultZoneThroughZoneOffset;
import com.dispatchflow.archfixture.alpha.domain.clock.ReadsTheSystemClock;
import com.dispatchflow.archfixture.alpha.domain.clock.SleepsTheThread;
import com.dispatchflow.archfixture.alpha.domain.clock.UsesACalendar;
import com.dispatchflow.archfixture.alpha.domain.clock.UsesALegacyDate;
import com.dispatchflow.archfixture.alpha.domain.dependency.AnnotatedWithModulith;
import com.dispatchflow.archfixture.alpha.domain.dependency.DependsOnAFramework;
import com.dispatchflow.archfixture.alpha.domain.dependency.DependsOnAnotherModulesDomain;
import com.dispatchflow.archfixture.alpha.domain.dependency.DependsOnAnotherModulesEvents;
import com.dispatchflow.archfixture.alpha.domain.dependency.DependsOnItsApplication;
import com.dispatchflow.archfixture.alpha.domain.exception.CatchesAnIllegalArgument;
import com.dispatchflow.archfixture.alpha.domain.exception.CreatesANumberFormatException;
import com.dispatchflow.archfixture.alpha.domain.exception.CreatesAnIllegalArgument;
import com.dispatchflow.archfixture.alpha.domain.exception.CreatesAnIllegalState;
import com.dispatchflow.archfixture.alpha.domain.exception.CreatesAnIllegalStateByReference;
import com.dispatchflow.archfixture.alpha.domain.id.GeneratesARandomUuid;
import com.dispatchflow.archfixture.alpha.domain.id.GeneratesARandomUuidByReference;
import com.dispatchflow.archfixture.alpha.domain.io.BuildsADatePatternWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.CallsOverHttp;
import com.dispatchflow.archfixture.alpha.domain.io.FormatsATemplateWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.FormatsWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.LoadsAResourceBundle;
import com.dispatchflow.archfixture.alpha.domain.io.LoadsAService;
import com.dispatchflow.archfixture.alpha.domain.io.LowerCasesWithTheDefaultLocaleByReference;
import com.dispatchflow.archfixture.alpha.domain.io.OpensASocket;
import com.dispatchflow.archfixture.alpha.domain.io.PrintsToStandardOut;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAFile;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAPath;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAResourceThroughAClassLoader;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAResourceThroughItsClass;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsTheDefaultLocaleOfACategory;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsTheEnvironment;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsTheJvmsManagement;
import com.dispatchflow.archfixture.alpha.domain.io.SetsTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.StartsAPlatformThread;
import com.dispatchflow.archfixture.alpha.domain.io.StartsAProcess;
import com.dispatchflow.archfixture.alpha.domain.io.StartsAThread;
import com.dispatchflow.archfixture.alpha.domain.io.StartsAVirtualThread;
import com.dispatchflow.archfixture.alpha.domain.io.UpperCasesWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.UsesAnExecutor;
import com.dispatchflow.archfixture.alpha.domain.io.UsesPreferences;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithJul;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithSlf4j;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithTheSystemLogger;
import com.dispatchflow.archfixture.alpha.domain.randomness.ShufflesAList;
import com.dispatchflow.archfixture.alpha.domain.randomness.UsesARandomGenerator;
import com.dispatchflow.archfixture.alpha.domain.randomness.UsesMathRandom;
import com.dispatchflow.archfixture.alpha.domain.randomness.UsesRandom;
import com.dispatchflow.archfixture.alpha.domain.randomness.UsesThreadLocalRandom;
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
import com.dispatchflow.archfixture.shared.KernelDependsOnJts;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.tngtech.archunit.core.domain.JavaClasses;
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

    private static final String FIXTURE_ROOT = FixtureClasses.ROOT;
    private static final String FIXTURE_MODULE_ANNOTATIONS = NamedInterface.class.getPackageName();

    private static boolean flags(ArchRule rule, Class<?>... fixtures) {
        return rule.evaluate(FixtureClasses.only(fixtures)).hasViolation();
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
                KernelDependsOnJts.class,
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
        JavaClasses gamma = FixtureClasses.inPackage("gamma");

        assertThat(gamma.stream().map(type -> type.getSimpleName())).contains("package-info");
        assertThat(rule1().evaluate(gamma).hasViolation()).isFalse();
    }

    @Test
    void GivenAnotherFrameworksAnnotationOnADomainPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses delta = FixtureClasses.inPackage("delta");

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
                ReadsNanoTime.class,
                SleepsTheThread.class,
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
                StartsAProcess.class,
                UsesPreferences.class,
                ReadsTheJvmsManagement.class,
                LoadsAResourceBundle.class,
                ReadsTheDefaultLocale.class,
                ReadsTheDefaultLocaleOfACategory.class,
                SetsTheDefaultLocale.class,
                UpperCasesWithTheDefaultLocale.class,
                LowerCasesWithTheDefaultLocaleByReference.class,
                FormatsWithTheDefaultLocale.class,
                FormatsATemplateWithTheDefaultLocale.class,
                BuildsADatePatternWithTheDefaultLocale.class,
                LoadsAService.class,
                ReadsAResourceThroughItsClass.class,
                ReadsAResourceThroughAClassLoader.class,
                StartsAThread.class,
                StartsAPlatformThread.class,
                StartsAVirtualThread.class,
                UsesAnExecutor.class
            })
    void GivenIo_WhenRule4Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(DomainPurityRules.noIo(FIXTURE_ROOT), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(classes = {AlphaValue.class, AlphaAggregate.class})
    void GivenIdnAFixedLocaleOrNoIoAtAll_WhenRule4Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
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
