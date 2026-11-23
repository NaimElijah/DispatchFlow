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
import com.dispatchflow.archfixture.alpha.domain.clock.SleepsThroughATimeUnit;
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
import com.dispatchflow.archfixture.alpha.domain.io.CallsOverHttp;
import com.dispatchflow.archfixture.alpha.domain.io.OpensASocket;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAFile;
import com.dispatchflow.archfixture.alpha.domain.io.ReadsAPath;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.CallsAMethodByReflection;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.LoadsAClassByName;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.LoadsAResourceBundle;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.LoadsAService;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.LooksUpAMethodHandle;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.ReadsAResourceAsAStream;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.ReadsAResourceThroughAClassLoader;
import com.dispatchflow.archfixture.alpha.domain.io.classpath.ReadsAResourceThroughItsClass;
import com.dispatchflow.archfixture.alpha.domain.io.locale.BuildsADatePatternWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.BuildsADecimalFormatWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.BuildsAFormatterWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.BuildsALocalizedDateFormatter;
import com.dispatchflow.archfixture.alpha.domain.io.locale.FormatsADateWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.FormatsAMessageWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.FormatsANumberWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.FormatsATemplateWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.FormatsWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.LowerCasesWithTheDefaultLocaleByReference;
import com.dispatchflow.archfixture.alpha.domain.io.locale.ReadsTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.ReadsTheDefaultLocaleOfACategory;
import com.dispatchflow.archfixture.alpha.domain.io.locale.SetsTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.locale.UpperCasesWithTheDefaultLocale;
import com.dispatchflow.archfixture.alpha.domain.io.process.PrintsToStandardOut;
import com.dispatchflow.archfixture.alpha.domain.io.process.ReadsABooleanProperty;
import com.dispatchflow.archfixture.alpha.domain.io.process.ReadsALongProperty;
import com.dispatchflow.archfixture.alpha.domain.io.process.ReadsAnIntegerProperty;
import com.dispatchflow.archfixture.alpha.domain.io.process.ReadsTheEnvironment;
import com.dispatchflow.archfixture.alpha.domain.io.process.ReadsTheJvmsManagement;
import com.dispatchflow.archfixture.alpha.domain.io.process.StartsAProcess;
import com.dispatchflow.archfixture.alpha.domain.io.process.UsesPreferences;
import com.dispatchflow.archfixture.alpha.domain.io.thread.BuildsAThreadPoolExecutor;
import com.dispatchflow.archfixture.alpha.domain.io.thread.ContinuesAsyncOnACompletionStage;
import com.dispatchflow.archfixture.alpha.domain.io.thread.MakesAStreamParallel;
import com.dispatchflow.archfixture.alpha.domain.io.thread.RegistersWithACleaner;
import com.dispatchflow.archfixture.alpha.domain.io.thread.RunsAsyncOnACompletableFuture;
import com.dispatchflow.archfixture.alpha.domain.io.thread.RunsInItsOwnThread;
import com.dispatchflow.archfixture.alpha.domain.io.thread.SchedulesOnATimer;
import com.dispatchflow.archfixture.alpha.domain.io.thread.StartsAPlatformThread;
import com.dispatchflow.archfixture.alpha.domain.io.thread.StartsAThread;
import com.dispatchflow.archfixture.alpha.domain.io.thread.StartsAVirtualThread;
import com.dispatchflow.archfixture.alpha.domain.io.thread.StreamsInParallel;
import com.dispatchflow.archfixture.alpha.domain.io.thread.UsesAnExecutor;
import com.dispatchflow.archfixture.alpha.domain.io.thread.UsesTheCommonPool;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithJul;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithSlf4j;
import com.dispatchflow.archfixture.alpha.domain.logging.LogsWithTheSystemLogger;
import com.dispatchflow.archfixture.alpha.domain.mapping.CarriesAnotherHibernateAnnotation;
import com.dispatchflow.archfixture.alpha.domain.mapping.MappedWithHibernateAndSpringData;
import com.dispatchflow.archfixture.alpha.domain.mapping.MappedWithJakartaPersistence;
import com.dispatchflow.archfixture.alpha.domain.mapping.UsesASpringDataType;
import com.dispatchflow.archfixture.alpha.domain.mapping.UsesTheEntityManager;
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
import com.dispatchflow.archfixture.alpha.events.EventWithAJpaAnnotation;
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
import com.dispatchflow.archfixture.shared.KernelMappedSuperclass;
import com.dispatchflow.archfixture.shared.KernelValue;
import com.dispatchflow.archfixture.shared.adapter.out.persistence.KernelAdapterUsesJpa;
import com.dispatchflow.archfixture.shared.application.SharedPortCarriesAJpaAnnotation;
import com.dispatchflow.archfixture.shared.application.SharedPortIsTransactional;
import com.tngtech.archunit.core.domain.JavaClass;
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
    // A root of its own, so its kernel application package-info can carry another module annotation
    // beside the one archfixture.shared.application carries.
    private static final String ETA = "eta";

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
                AnnotatedWithModulith.class,
                SharedPortIsTransactional.class,
                UsesTheEntityManager.class,
                UsesASpringDataType.class,
                CarriesAnotherHibernateAnnotation.class,
                EventWithAJpaAnnotation.class,
                SharedPortCarriesAJpaAnnotation.class
            })
    void GivenAForbiddenDependency_WhenRule1Evaluates_ThenItIsFlagged(Class<?> fixture) {
        assertThat(flags(rule1(), fixture)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                AlphaValue.class,
                AlphaEventKind.class,
                AlphaHappened.class,
                KernelValue.class,
                MappedWithJakartaPersistence.class,
                MappedWithHibernateAndSpringData.class,
                KernelMappedSuperclass.class
            })
    void GivenOnlyAllowedDependencies_WhenRule1Evaluates_ThenNothingIsFlagged(Class<?> fixture) {
        assertThat(flags(rule1(), fixture)).isFalse();
    }

    @Test
    void GivenTheSharedAdapterBesideTheKernel_WhenThePurityRulesEvaluate_ThenNothingIsFlagged() {
        assertThat(flags(rule1(), KernelAdapterUsesJpa.class, KernelValue.class))
                .isFalse();
        assertThat(flags(DomainPurityRules.noIo(FIXTURE_ROOT), KernelAdapterUsesJpa.class, KernelValue.class))
                .isFalse();
        assertThat(flags(DomainPurityRules.noLogging(FIXTURE_ROOT), KernelAdapterUsesJpa.class, KernelValue.class))
                .isFalse();
    }

    @Test
    void GivenModuleAnnotationsOnPackageInfos_WhenRule1Evaluates_ThenNothingIsFlagged() {
        JavaClasses gamma = FixtureClasses.inPackage("gamma");

        assertThat(gamma.stream().map(type -> type.getSimpleName())).contains("package-info");
        assertThat(rule1().evaluate(gamma).hasViolation()).isFalse();
    }

    @Test
    void GivenAModuleAnnotationOnTheKernelsApplicationPackageInfo_WhenRule1Evaluates_ThenNothingIsFlagged() {
        JavaClasses packageInfo =
                FixtureClasses.inPackage("shared.application").that(JavaClass.Predicates.simpleName("package-info"));

        assertThat(packageInfo).hasSize(1);
        assertThat(rule1().evaluate(packageInfo).hasViolation()).isFalse();
    }

    @Test
    void GivenAModuleAnnotationOnAnotherKernelPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses packageInfo =
                FixtureClasses.inPackage("shared.other").that(JavaClass.Predicates.simpleName("package-info"));

        assertThat(packageInfo).hasSize(1);
        assertThat(rule1().evaluate(packageInfo).hasViolation()).isTrue();
    }

    @Test
    void GivenAnotherModuleAnnotationOnTheKernelsApplicationPackageInfo_WhenRule1Evaluates_ThenItIsFlagged() {
        JavaClasses packageInfo = FixtureClasses.inPackage(ETA + ".shared.application")
                .that(JavaClass.Predicates.simpleName("package-info"));

        assertThat(packageInfo).hasSize(1);
        assertThat(DomainPurityRules.onlyAllowedDependencies(FIXTURE_ROOT + "." + ETA, FIXTURE_MODULE_ANNOTATIONS)
                        .evaluate(packageInfo)
                        .hasViolation())
                .isTrue();
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
                SleepsThroughATimeUnit.class,
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
                UsesAnExecutor.class,
                ReadsAnIntegerProperty.class,
                ReadsALongProperty.class,
                ReadsABooleanProperty.class,
                CallsAMethodByReflection.class,
                LoadsAClassByName.class,
                LooksUpAMethodHandle.class,
                ReadsAResourceAsAStream.class,
                FormatsANumberWithTheDefaultLocale.class,
                BuildsADecimalFormatWithTheDefaultLocale.class,
                FormatsAMessageWithTheDefaultLocale.class,
                FormatsADateWithTheDefaultLocale.class,
                BuildsAFormatterWithTheDefaultLocale.class,
                BuildsALocalizedDateFormatter.class,
                RunsInItsOwnThread.class,
                RunsAsyncOnACompletableFuture.class,
                ContinuesAsyncOnACompletionStage.class,
                UsesTheCommonPool.class,
                StreamsInParallel.class,
                MakesAStreamParallel.class,
                SchedulesOnATimer.class,
                RegistersWithACleaner.class,
                BuildsAThreadPoolExecutor.class
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
