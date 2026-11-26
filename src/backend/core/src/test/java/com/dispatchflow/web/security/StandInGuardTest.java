package com.dispatchflow.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.StandInProfiles;
import com.dispatchflow.TestActors;
import com.dispatchflow.access.application.api.ActorRole;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The stand-in session's fail-closed guards (ADR-121 decision 5, ADR-140 decision 4, ADR-147): it
 * exists only when the active profiles are exactly {@code dev} or exactly {@code test}, and the core
 * refuses to boot with its configuration under any other profiles or in a deployed environment.
 * Each context reads the core's own property files, as a real start does, so the {@code dev} and
 * {@code test} profiles import the very list the core imports.
 */
class StandInGuardTest {

    // A value no message may quote: a deployed key's value is a secret.
    private static final String SECRET = "s3cr3t-value-that-no-message-quotes";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // The process's own variables first, so none a developer exported reaches the profiles
            // or the files the config-data step reads.
            .withInitializer(ProcessEnvironment.empty())
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(StandInGuard.class, StandInSession.class);

    private ApplicationContextRunner under(String profiles) {
        return runner.withPropertyValues("spring.profiles.active=" + profiles);
    }

    /** The dev profile, with the account a run names, as just core-run and the Compose core do. */
    private ApplicationContextRunner underDev() {
        return under(StandInProfiles.DEV).withPropertyValues(StandInSession.ACCOUNT + "=dispatcher");
    }

    /** An environment variable, as the process's environment holds one. */
    private static ApplicationContextInitializer<ConfigurableApplicationContext> exported(String name, String value) {
        return ProcessEnvironment.holding(Map.of(name, value));
    }

    private static void refusedNaming(AssertableApplicationContext context, String named) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(named)
                .hasMessageNotContaining(SECRET);
    }

    /** The principal the stand-in session gives a request that presents no credential. */
    private static SignedInCaller signedIn(AssertableApplicationContext context) throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        context.getBean(StandInSession.class).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SignedInCaller.of(request).orElseThrow();
    }

    @Test
    void GivenOnlyTheDevProfile_WhenTheContextStarts_ThenTheStandInSessionExists() {
        underDev().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StandInSession.class);
            assertThat(signedIn(context).roles()).containsExactly(ActorRole.DISPATCHER);
        });
    }

    @Test
    void GivenOnlyTheTestProfile_WhenTheContextStarts_ThenTheStandInSessionExists() {
        under(StandInProfiles.TEST).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StandInSession.class);
            assertThat(signedIn(context).roles()).containsExactly(ActorRole.DISPATCHER);
        });
    }

    @Test
    void GivenOnlyTheDevProfileAndNoAccountNamed_WhenTheContextStarts_ThenItRefusesToStart() {
        // The main classpath's dev file names no account, so a deployment that runs dev by mistake
        // stops here, with or without the deployed marker.
        under(StandInProfiles.DEV).run(context -> refusedNaming(context, "is unset"));
    }

    @Test
    void GivenNoGuardBean_WhenTheStandInSessionIsMadeInADeployedEnvironment_ThenItRefusesToStart() {
        new ApplicationContextRunner()
                .withInitializer(ProcessEnvironment.empty())
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(StandInSession.class)
                .withPropertyValues(
                        "spring.profiles.active=" + StandInProfiles.DEV,
                        StandInSession.ACCOUNT + "=dispatcher",
                        StandInProfiles.DEPLOYED_MARKER + "=" + SECRET)
                .run(context -> refusedNaming(context, StandInProfiles.DEPLOYED_MARKER));
    }

    @Test
    void GivenTheMainClasspathsDevFile_WhenItIsRead_ThenItNamesNoAccount() throws IOException {
        Properties dev = new Properties();
        try (InputStream read = StandInGuardTest.class.getResourceAsStream("/application-dev.properties")) {
            assertThat(read).isNotNull();
            dev.load(read);
        }

        assertThat(dev.stringPropertyNames()).noneMatch(name -> name.startsWith(StandInProfiles.STAND_IN));
    }

    @Test
    void GivenDevAndAnotherProfile_WhenTheContextStarts_ThenItRefusesToStart() {
        under("dev,pilot").run(context -> refusedNaming(context, "[dev, pilot]"));
    }

    @Test
    void GivenTestAndDev_WhenTheContextStarts_ThenItRefusesToStart() {
        under("test,dev").run(context -> refusedNaming(context, "[test, dev]"));
    }

    @Test
    void GivenLazyInitialization_WhenTheContextStartsUnderDevAndAnother_ThenItStillRefusesToStart() {
        under("dev,pilot")
                .withInitializer(context ->
                        context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .run(context -> refusedNaming(context, "[dev, pilot]"));
    }

    @Test
    void GivenLazyInitialization_WhenTheContextStartsInADeployedEnvironment_ThenItStillRefusesToStart() {
        under(StandInProfiles.DEV)
                .withPropertyValues(StandInProfiles.DEPLOYED_MARKER + "=" + SECRET)
                .withInitializer(context ->
                        context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                .run(context -> refusedNaming(context, StandInProfiles.DEPLOYED_MARKER));
    }

    @Test
    void GivenDevAndE2e_WhenTheContextStarts_ThenItRefusesToStart() {
        under("dev,e2e").run(context -> refusedNaming(context, "[dev, e2e]"));
    }

    @Test
    void GivenTheStandInsPropertyUnderNoProfile_WhenTheContextStarts_ThenItRefusesToStart() {
        runner.withPropertyValues(StandInSession.ACCOUNT + "=dispatcher")
                .run(context -> refusedNaming(context, "the active profiles are []"));
    }

    @Test
    void GivenTheDeployedMarker_WhenTheContextStarts_ThenItRefusesToStart() {
        under(StandInProfiles.DEV)
                .withPropertyValues(StandInProfiles.DEPLOYED_MARKER + "=" + SECRET)
                .run(context -> refusedNaming(context, StandInProfiles.DEPLOYED_MARKER));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"dispatchflow.sms.credential", "dispatchflow.email.credential", "dispatchflow.push.credential"})
    void GivenAProviderCredential_WhenTheContextStarts_ThenItRefusesToStart(String credential) {
        under(StandInProfiles.TEST)
                .withPropertyValues(credential + "=" + SECRET)
                .run(context -> refusedNaming(context, credential));
    }

    @Test
    void GivenTheDeployedMarkerAsTheEnvironmentVariable_WhenTheContextStarts_ThenItRefusesToStart() {
        under(StandInProfiles.DEV)
                .withInitializer(exported("DISPATCHFLOW_DEPLOYED", SECRET))
                .run(context -> refusedNaming(context, StandInProfiles.DEPLOYED_MARKER));
    }

    @Test
    void GivenAProviderCredentialAsAnEnvironmentVariable_WhenTheContextStarts_ThenItRefusesToStart() {
        under(StandInProfiles.TEST)
                .withInitializer(exported("DISPATCHFLOW_SMS_CREDENTIAL", SECRET))
                .run(context -> refusedNaming(context, "dispatchflow.sms.credential"));
    }

    @Test
    void GivenTheStandInsAccountExportedUnderNoProfile_WhenTheContextStarts_ThenItRefusesToStart() {
        runner.withInitializer(exported("DISPATCHFLOW_STANDIN_ACCOUNT", "dispatcher"))
                .run(context -> refusedNaming(context, "the active profiles are []"));
    }

    @Test
    void GivenTheStandInsAccountExportedUnderDev_WhenTheContextStarts_ThenItActsAsThatEntry() {
        under(StandInProfiles.DEV)
                .withInitializer(exported("DISPATCHFLOW_STANDIN_ACCOUNT", "admin"))
                .run(context -> assertThat(signedIn(context).roles()).containsExactly(ActorRole.ADMIN));
    }

    @Test
    void GivenTheDeployedMarkerUnderNoProfile_WhenTheContextStarts_ThenItStartsWithNoStandIn() {
        runner.withPropertyValues(StandInProfiles.DEPLOYED_MARKER + "=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class));
    }

    @Test
    void GivenDevOnlyAsTheDefaultProfile_WhenTheContextStarts_ThenItRefusesToStart() {
        // A default profile is no active one, so the stand-in may not run, but Boot still reads
        // application-dev.properties for it, and with it the stand-in's configuration.
        runner.withPropertyValues("spring.profiles.default=" + StandInProfiles.DEV)
                .run(context -> refusedNaming(context, "the active profiles are []"));
    }

    @Test
    void GivenNoProfile_WhenTheContextStarts_ThenNoStandInSessionExists() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class));
    }

    @Test
    void GivenAnotherProfileAlone_WhenTheContextStarts_ThenNoStandInSessionExists() {
        under("pilot").run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class));
    }

    @Test
    void GivenAStandInAccountThatIsNoEntry_WhenTheContextStarts_ThenItRefusesToStart() {
        under(StandInProfiles.DEV)
                .withPropertyValues(StandInSession.ACCOUNT + "=nobody")
                .run(context -> refusedNaming(context, "nobody"));
    }

    @Test
    void GivenEitherTheDevOrTheTestProfile_WhenTheContextStarts_ThenTheSameSeededAccountsAreRead() {
        AtomicReference<SeededAccounts> underDev = new AtomicReference<>();
        AtomicReference<SignedInCaller> devStandIn = new AtomicReference<>();
        underDev().run(context -> {
            underDev.set(SeededAccounts.from(context.getEnvironment()));
            devStandIn.set(signedIn(context));
        });

        under(StandInProfiles.TEST).run(context -> {
            SeededAccounts underTest = SeededAccounts.from(context.getEnvironment());
            assertThat(underTest.entries()).containsOnlyKeys("admin", "dispatcher", "driver");
            assertThat(underTest).isEqualTo(underDev.get());
            assertThat(signedIn(context)).isEqualTo(devStandIn.get());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/application-dev.properties", "/application-test.properties"})
    void GivenAProfilesFile_WhenItIsRead_ThenItImportsTheOneListAndHoldsNoEntryOfItsOwn(String file)
            throws IOException {
        Properties profile = new Properties();
        try (InputStream read = StandInGuardTest.class.getResourceAsStream(file)) {
            assertThat(read).as(file).isNotNull();
            profile.load(read);
        }

        assertThat(profile.getProperty("spring.config.import")).isEqualTo("classpath:seeded-accounts.properties");
        assertThat(profile.stringPropertyNames()).noneMatch(name -> name.startsWith(SeededAccounts.PREFIX));
    }

    @Test
    void GivenTheStandIn_WhenComparedWithTestActorsDispatcher_ThenBothNameTheSameAccount() {
        under(StandInProfiles.TEST)
                .run(context -> assertThat(signedIn(context).account())
                        .isEqualTo(TestActors.dispatcher().account()));
    }

    @Test
    void GivenTheDriverEntryIsSelected_WhenTheContextStarts_ThenTheStandInIsTheSeededDriver() {
        under(StandInProfiles.DEV)
                .withPropertyValues(StandInSession.ACCOUNT + "=driver")
                .run(context -> {
                    SignedInCaller driver = signedIn(context);
                    assertThat(driver.account()).isEqualTo(TestActors.driver().account());
                    assertThat(driver.roles()).containsExactly(ActorRole.DRIVER);
                });
    }

    @Test
    void GivenTheDevProfile_WhenTheContextStarts_ThenTheServerBindsTheLoopbackInterface() {
        underDev()
                .run(context -> assertThat(context.getEnvironment().getProperty("server.address"))
                        .isEqualTo("127.0.0.1"));
    }
}
