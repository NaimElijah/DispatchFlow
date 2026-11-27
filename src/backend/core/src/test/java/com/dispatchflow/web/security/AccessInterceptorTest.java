package com.dispatchflow.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.dispatchflow.CapturedLogs;
import com.dispatchflow.StandInProfiles;
import com.dispatchflow.TestActors;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.ActorRole;
import com.dispatchflow.access.application.api.CurrentActor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.audit.application.api.CurrentCall;
import com.dispatchflow.audit.application.service.AppendedAuditRecords;
import com.dispatchflow.audit.application.service.AuditLogService;
import com.dispatchflow.audit.domain.AppVersion;
import com.dispatchflow.audit.domain.AuditOutcome;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.audit.domain.ClientApp;
import com.dispatchflow.audit.domain.DeclaredApp;
import com.dispatchflow.audit.domain.ErrorCode;
import com.dispatchflow.audit.domain.OperationName;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The caller of a call under {@code /api/v1}, end to end in Spring Boot's own MVC configuration
 * with every bean of {@code web} and no database: the stand-in session sets the principal, the
 * access interceptor builds the caller or answers 401, and the call wrapper binds the two holders
 * that a test-only controller reads (ADR-140 decisions 3, 4 and 7, ADR-147).
 */
class AccessInterceptorTest {

    private static final String PROBE = "/api/v1/access-interceptor-test/probe";
    private static final String RACE = "/api/v1/access-interceptor-test/race";
    private static final String OUTSIDE = "/access-interceptor-test/outside";
    private static final String NOTE = "/api/v1/access-interceptor-test/note";
    private static final String RETRIED = "/api/v1/access-interceptor-test/retried";
    private static final String COUNTED = "/api/v1/access-interceptor-test/counted";
    private static final String ANSWERED = "/api/v1/access-interceptor-test/answered";
    private static final String SHORT_NOTE = "/api/v1/access-interceptor-test/short-note";
    private static final int SHORT_NOTE_LENGTH = 4;
    // A value the contract refuses, which the refusal's exception quotes in its message.
    private static final String REFUSED_VALUE = "Dana Levi";
    // A path no operation has, which the static-resource handler serves with a 404.
    private static final String NO_OPERATION = "/api/v1/access-interceptor-test/no-operation";
    private static final String ROUTED = "/api/v1/access-interceptor-test/routed";
    private static final String LEGACY = "/api/v1/access-interceptor-test/legacy";
    private static final UUID PLAN = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final Instant NOW = Instant.parse("2026-03-15T08:00:00Z");
    private static final String AUDIT_LOG = "auditLog";
    private static final SignedInCaller SIGNED_IN =
            new SignedInCaller(TestActors.dispatcher().account(), StandInSession.SESSION, Set.of(ActorRole.DISPATCHER));

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(ProcessEnvironment.empty())
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withUserConfiguration(WebPackage.class)
            .withBean(Clock.class, FixedClock::new)
            .withBean(AUDIT_LOG, AuditLog.class, RecordingAuditLog::new)
            .withBean(ProbeController.class);

    @RegisterExtension
    final CapturedLogs logs = CapturedLogs.of(NotSignedInHandler.class);

    @RegisterExtension
    final CapturedLogs everyLine = CapturedLogs.ofEveryLogger();

    // What a handler that is no annotated handler method saw: how often it ran, and the caller kept.
    private final AtomicInteger handlerRuns = new AtomicInteger();
    private final AtomicReference<Optional<Caller>> keptCaller = new AtomicReference<>();

    /** Every bean of {@code web} and {@code web.security}, but none of the tests' own classes. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.dispatchflow.web",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*(Test|IT)(\\$.*)?"))
    static class WebPackage {}

    private record Fixture(AssertableWebApplicationContext context) {

        MockMvcTester mvc() {
            return mvcBehind();
        }

        /**
         * The core's filters behind Spring's forwarded-header filter, as {@code
         * server.forward-headers-strategy=framework} puts it first, which rewrites the server name
         * from {@code X-Forwarded-Host}.
         */
        MockMvcTester mvcBehindForwardedHeaders() {
            return mvcBehind(new ForwardedHeaderFilter());
        }

        private MockMvcTester mvcBehind(Filter... first) {
            Filter[] filters = Stream.concat(
                            Stream.of(first),
                            context.getBeanProvider(Filter.class).orderedStream())
                    .toArray(Filter[]::new);
            return MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(filters)
                    .build());
        }

        ProbeController controller() {
            return context.getBean(ProbeController.class);
        }

        RecordingAuditLog auditLog() {
            return (RecordingAuditLog) context.getBean(AuditLog.class);
        }
    }

    private void under(String profile, Consumer<Fixture> test) {
        runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(new Fixture(context));
        });
    }

    private void withNoStandIn(Consumer<Fixture> test) {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class);
            test.accept(new Fixture(context));
        });
    }

    private static void isTheOne401(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("ACCESS_TOKEN_REFUSED");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().hasSize(32);
    }

    @Test
    void GivenTheStandIn_WhenACallArrives_ThenTheActorNamesTheStandInAccountItsRolesAndTheOperation() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(PROBE).exchange();

            assertThat(result).hasStatusOk();
            Actor actor = fixture.controller().actor.get();
            assertThat(actor.account()).isEqualTo(TestActors.dispatcher().account());
            assertThat(actor.roles()).containsExactly(ActorRole.DISPATCHER);
            assertThat(actor.call().user()).contains(actor.account());
            assertThat(actor.call().session()).contains(StandInSession.SESSION);
            assertThat(actor.call().operation()).isEqualTo("readTheCaller");
            assertThat(actor.call().traceId()).hasSize(32);
            assertThat(fixture.controller().call.get()).isSameAs(actor.call());
            assertThat(fixture.controller().accountInTheMdc.get())
                    .isEqualTo(actor.account().value().toString());
        });
    }

    @Test
    void GivenATraceparent_WhenACallArrives_ThenItsCallContextCarriesThatTraceId() {
        under(StandInProfiles.TEST, fixture -> {
            fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                    .exchange();

            assertThat(fixture.controller().call.get().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        });
    }

    @Test
    void GivenXAppWebAndAVersion_WhenTheActorIsBuilt_ThenItsCallContextCarriesTheClientRelease() {
        under(StandInProfiles.TEST, fixture -> {
            fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(Caller.X_APP, "web")
                    .header(Caller.X_APP_VERSION, "1.4.0")
                    .exchange();

            assertThat(fixture.controller().actor.get().call().client())
                    .contains(new ClientRelease(ClientKind.WEB, "1.4.0"));
        });
    }

    @Test
    void GivenAnUnknownXApp_WhenTheActorIsBuilt_ThenTheClientIsLeftOut() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(Caller.X_APP, "desktop")
                    .header(Caller.X_APP_VERSION, "1.4.0")
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().actor.get().call().client()).isEmpty();
        });
    }

    @Test
    void GivenTheDriverEntryIsSelected_WhenACallArrives_ThenTheActorHoldsOnlyTheDriverRole() {
        runner.withPropertyValues("spring.profiles.active=" + StandInProfiles.TEST, StandInSession.ACCOUNT + "=driver")
                .run(context -> {
                    Fixture fixture = new Fixture(context);
                    fixture.mvc().get().uri(PROBE).exchange();

                    Actor actor = fixture.controller().actor.get();
                    assertThat(actor.account()).isEqualTo(TestActors.driver().account());
                    assertThat(actor.roles()).containsExactly(ActorRole.DRIVER);
                });
    }

    @Test
    void GivenNoStandIn_WhenAnOperationNeedingAnActorIsCalled_Then401() {
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(PROBE).exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    // A plain OPTIONS, no CORS preflight, reaches Spring's own OPTIONS handler method.
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void GivenNoPrincipal_WhenAReadIsCalled_Then401AndNoAuditRecord(String method) {
        withNoStandIn(fixture -> {
            MvcTestResult result =
                    fixture.mvc().method(HttpMethod.valueOf(method)).uri(PROBE).exchange();

            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                    .containsExactly("Bearer");
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenNoPrincipalAndAnInvalidBody_WhenAChangingOperationIsCalled_Then401NotTheBodysRefusal() {
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(NOTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"undeclared\":1}")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.auditLog().refusals)
                    .singleElement()
                    .satisfies(refusal -> assertThat(refusal.call().operation()).isEqualTo("sendANote"));
        });
    }

    @Test
    void GivenASignedInChangingCallWithAnInvalidBody_WhenTheContractRefusesIt_Then400AndOneRecordWithItsCode() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(NOTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"undeclared\":1}")
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fixture.controller().actor.get()).isNull();
            assertThat(fixture.auditLog().refusals).singleElement().satisfies(refusal -> {
                assertThat(refusal.errorCode()).isEqualTo("INVALID_REQUEST");
                assertThat(refusal.call().operation()).isEqualTo("sendANote");
                assertThat(refusal.call().user())
                        .contains(TestActors.dispatcher().account());
                assertThat(refusal.at()).isEqualTo(NOW);
            });
        });
    }

    @Test
    void GivenASignedInReadThatBreaksTheContract_WhenItIsRefused_Then400AndNoRecord() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result =
                    fixture.mvc().get().uri(COUNTED).param("count", "many").exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenASignedInChangingCallWhoseAnswerCantBeWritten_WhenRefusedAfterItsHandlerRan_ThenNoRecord() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(ANSWERED)
                    .accept(MediaType.IMAGE_PNG)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_ACCEPTABLE);
            assertThat(fixture.controller().actor.get()).isNotNull();
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenTheAuditLogFails_WhenTheContractRefusesASignedInChangingCall_Then500AndOneErrorLineWithNoRefusedValue() {
        under(StandInProfiles.TEST, fixture -> {
            fixture.auditLog().failing = true;

            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(SHORT_NOTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"" + REFUSED_VALUE + "\"}")
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("UNEXPECTED_ERROR");
            assertThat(everyLine.at(Level.ERROR)).singleElement().satisfies(line -> {
                assertThat(line.getLoggerName()).isEqualTo("com.dispatchflow.web.ContractErrorHandler");
                assertThat(line.getThrowableProxy().getMessage()).isEqualTo("the audit log failed");
                assertThat(CapturedLogs.keyValues(line))
                        .containsEntry("code", "INVALID_REQUEST")
                        .containsEntry("exception", "MethodArgumentNotValidException");
            });
            assertThat(everyLine.events())
                    .allSatisfy(line ->
                            assertThat(CapturedLogs.textsOf(line)).noneMatch(text -> text.contains(REFUSED_VALUE)));
        });
    }

    @Test
    void GivenAnErrorDispatchOfASignedInChangingCall_WhenTheContractRefusesIt_ThenNoRecord() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(NOTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"undeclared\":1}")
                    .with(request -> {
                        MDC.put(Problems.TRACE_ID, TestActors.TRACE_ID);
                        Caller.build(request, Optional.of(SIGNED_IN), "sendANote")
                                .keepFor(request);
                        MDC.clear();
                        request.setDispatcherType(DispatcherType.ERROR);
                        return request;
                    })
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenASignedInChangingCallOverTheBodyLimit_WhenTheContractRefusesIt_Then413AndOneRecordWithItsCode() {
        under(StandInProfiles.TEST, fixture -> {
            // One byte over BodySizeLimit.MAX_BODY_BYTES, which is package-private to web.
            String overTheLimit = "{\"text\":\"" + "a".repeat(1_048_576) + "\"}";

            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(NOTE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(overTheLimit)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
            assertThat(fixture.auditLog().refusals)
                    .singleElement()
                    .satisfies(refusal -> assertThat(refusal.errorCode()).isEqualTo("BODY_TOO_LARGE"));
        });
    }

    @Test
    void GivenTheAuditLogFails_WhenAChangingCallIsRefused_ThenItAnswers500AndNoCallerIsServed() {
        withNoStandIn(fixture -> {
            fixture.auditLog().failing = true;

            MvcTestResult result = fixture.mvc().post().uri(PROBE).exchange();

            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("UNEXPECTED_ERROR");
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isNull();
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @Test
    void GivenASignedInCallThatLosesARaceOnce_WhenItIsRetried_ThenTheRetryReadsTheSameActor() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(RETRIED).exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().tries).hasValue(2);
            assertThat(fixture.controller().actor.get().account())
                    .isEqualTo(TestActors.dispatcher().account());
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void GivenNoPrincipal_WhenAChangingOperationIsCalled_Then401WithTheBareChallengeAndARefusedAuditRecord(
            String method) {
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc()
                    .method(HttpMethod.valueOf(method))
                    .uri(PROBE)
                    .header(Caller.X_APP, "phone")
                    .header(Caller.X_APP_VERSION, "2.0.1")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
            assertThat(fixture.auditLog().refusals).singleElement().satisfies(refusal -> {
                assertThat(refusal.errorCode()).isEqualTo("ACCESS_TOKEN_REFUSED");
                assertThat(refusal.at()).isEqualTo(NOW);
                assertThat(refusal.call().user()).isEmpty();
                assertThat(refusal.call().session()).isEmpty();
                assertThat(refusal.call().operation())
                        .isEqualTo(method.equals("POST") ? "changeSomething" : "changeSomethingElse");
                assertThat(refusal.call().client()).contains(new ClientRelease(ClientKind.PHONE, "2.0.1"));
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "DELETE"})
    void GivenNoPrincipal_WhenAChangingOperationIsCalledWithTheRealAuditLog_Then401AndAuditAcceptsItsRecord(
            String method) {
        AppendedAuditRecords records = new AppendedAuditRecords();
        runner.withAllowBeanDefinitionOverriding(true)
                .withBean(AUDIT_LOG, AuditLog.class, () -> new AuditLogService(records))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class);
                    MvcTestResult result = new Fixture(context)
                            .mvc()
                            .method(HttpMethod.valueOf(method))
                            .uri(PROBE)
                            .header(Caller.X_APP, "phone")
                            .header(Caller.X_APP_VERSION, "2.0.1")
                            .exchange();

                    isTheOne401(result);
                    AuditRecord record = records.only();
                    assertThat(record.outcome()).isEqualTo(AuditOutcome.refused(ErrorCode.of("ACCESS_TOKEN_REFUSED")));
                    assertThat(record.operation())
                            .isEqualTo(OperationName.of(
                                    method.equals("POST") ? "changeSomething" : "changeSomethingElse"));
                    assertThat(record.app()).contains(new DeclaredApp(ClientApp.PHONE, new AppVersion(2, 0, 1)));
                    assertThat(record.user()).isEmpty();
                    assertThat(record.at()).isEqualTo(NOW);
                });
    }

    @Test
    void GivenAFailingToken_WhenTheStandInExists_Then401() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(PROBE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJub25lIn0.e30.")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
            assertThat(fixture.auditLog().refusals)
                    .singleElement()
                    .satisfies(refusal -> assertThat(refusal.call().user()).isEmpty());
        });
    }

    @Test
    void GivenOnlyTheRefreshCookie_WhenTheStandInExists_ThenItServesNothing() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .cookie(new Cookie(StandInSession.REFRESH_COOKIE, "an-opaque-refresh-token"))
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @Test
    void GivenACrossSiteRequestFromAPage_WhenTheStandInExists_ThenItServesNothing() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(PROBE)
                    .header(HttpHeaders.ORIGIN, "https://evil.example")
                    .header("Sec-Fetch-Site", "cross-site")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @Test
    void GivenAHostNameRebindToTheLoopback_WhenTheStandInExists_ThenItServesNothing() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri("http://evil.example:8080" + PROBE)
                    .header("Sec-Fetch-Site", "same-origin")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @Test
    void GivenASameSiteRequestFromAPageOnAnotherPort_WhenTheStandInExists_ThenItServesNothing() {
        // A site ignores the port, so any page on localhost is same-site with the core.
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .post()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, "localhost:8080")
                    .header(HttpHeaders.ORIGIN, "http://localhost:8888")
                    .header("Sec-Fetch-Site", "same-site")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cross-site", "same-site", "an-unknown-value", ""})
    void GivenAnyOtherSecFetchSite_WhenTheStandInExists_ThenItServesNothing(String site) {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, "localhost:8080")
                    .header("Sec-Fetch-Site", site)
                    .exchange();

            isTheOne401(result);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-origin", "none"})
    void GivenTheWebAppsOwnOriginOrATypedAddress_WhenTheStandInExists_ThenItIsServed(String site) {
        // The web app reaches the core through its own origin's proxy, which keeps the Host.
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, "localhost:5173")
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header("Sec-Fetch-Site", site)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().actor.get()).isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"localhost", "localhost:8080", "LOCALHOST:8080", "LocalHost", "127.0.0.1:8080", "[::1]:8080"})
    void GivenALoopbackHostHeader_WhenTheStandInExists_ThenItIsServed(String host) {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, host)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().actor.get()).isNotNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.example:8080", "localhost.evil.example", "localhost.", "[::1", ":8080", ""})
    void GivenAnotherHostHeader_WhenTheStandInExists_ThenItServesNothing(String host) {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, host)
                    .exchange();

            isTheOne401(result);
        });
    }

    @Test
    void GivenARebindingPageThatForgesTheForwardedHost_WhenForwardedHeadersAreTrusted_ThenItServesNothing() {
        // The rebinding page's origin is no secure context, so its browser sends no Sec-Fetch-Site,
        // and it may add any header to its own origin. The filter rewrites the server name from
        // X-Forwarded-Host, so only the Host header itself still tells the page's own name.
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvcBehindForwardedHeaders()
                    .post()
                    .uri(PROBE)
                    .header(HttpHeaders.HOST, "evil.example:8080")
                    .header("X-Forwarded-Host", "localhost")
                    .exchange();

            isTheOne401(result);
            assertThat(fixture.controller().actor.get()).isNull();
        });
    }

    @Test
    void GivenNoPrincipal_WhenAPathNoOperationHasIsCalled_ThenTheOne401AndNoAuditRecord() {
        withNoStandIn(fixture -> {
            MvcTestResult read = fixture.mvc().get().uri(NO_OPERATION).exchange();
            MvcTestResult change = fixture.mvc().post().uri(NO_OPERATION).exchange();

            isTheOne401(read);
            isTheOne401(change);
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenTheStandIn_WhenAPathNoOperationHasIsCalled_ThenNoSuchOperation() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(NO_OPERATION).exchange();

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("NO_SUCH_OPERATION");
        });
    }

    @Test
    void GivenNoPrincipal_WhenARouterFunctionUnderApiV1IsCalled_ThenTheOne401() {
        runner.withBean("routed", RouterFunction.class, this::routed).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class);

            isTheOne401(new Fixture(context).mvc().get().uri(ROUTED).exchange());
            assertThat(handlerRuns).hasValue(0);
        });
    }

    @Test
    void GivenTheStandIn_WhenARouterFunctionUnderApiV1IsCalled_ThenItIsServedWithNoCallerKept() {
        runner.withBean("routed", RouterFunction.class, this::routed)
                .withPropertyValues("spring.profiles.active=" + StandInProfiles.TEST)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    MvcTestResult result =
                            new Fixture(context).mvc().get().uri(ROUTED).exchange();

                    assertThat(result).hasStatusOk();
                    assertThat(handlerRuns).hasValue(1);
                    assertThat(keptCaller.get()).isEmpty();
                });
    }

    @Test
    void GivenNoPrincipal_WhenAnHttpRequestHandlerUnderApiV1IsCalled_Then401AndTheHandlerNeverRuns() {
        // Spring's advice never sees an exception of this handler type, so the status the refusal
        // carries answers it; Tomcat then dispatches it to the error endpoint (WebOverTomcatIT).
        runner.withBean(LEGACY, HttpRequestHandler.class, this::legacy).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(StandInSession.class);

            MvcTestResult result = new Fixture(context).mvc().post().uri(LEGACY).exchange();

            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(handlerRuns).hasValue(0);
            assertThat(new Fixture(context).auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenTheStandIn_WhenAnHttpRequestHandlerUnderApiV1IsCalled_ThenItIsServedWithNoCallerKept() {
        runner.withBean(LEGACY, HttpRequestHandler.class, this::legacy)
                .withPropertyValues("spring.profiles.active=" + StandInProfiles.TEST)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    MvcTestResult result =
                            new Fixture(context).mvc().get().uri(LEGACY).exchange();

                    assertThat(result).hasStatusOk();
                    assertThat(handlerRuns).hasValue(1);
                    assertThat(keptCaller.get()).isEmpty();
                });
    }

    /** A handler under {@code /api/v1} that is no annotated handler method: a router function. */
    private RouterFunction<ServerResponse> routed() {
        return RouterFunctions.route()
                .GET(ROUTED, request -> {
                    handlerRuns.incrementAndGet();
                    keptCaller.set(Caller.of(request.servletRequest()));
                    return ServerResponse.ok().body("routed");
                })
                .build();
    }

    /** Another: an {@code HttpRequestHandler} bean, which its bean name maps to {@link #LEGACY}. */
    private HttpRequestHandler legacy() {
        return (request, response) -> {
            handlerRuns.incrementAndGet();
            keptCaller.set(Caller.of(request));
            response.getWriter().write("legacy");
        };
    }

    @Test
    void GivenNoPrincipal_WhenTheCallIsRefused_ThenOneInfoLineNamesTheStatusAndTheCode() {
        withNoStandIn(fixture -> {
            fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJub25lIn0.e30.")
                    .exchange();

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(CapturedLogs.keyValues(event))
                        .containsOnly(
                                Map.entry("status", HttpStatus.UNAUTHORIZED.value()),
                                Map.entry("code", "ACCESS_TOKEN_REFUSED"));
                assertThat(CapturedLogs.textsOf(event))
                        .noneSatisfy(text -> assertThat(text).contains("eyJ"));
                assertThat(event.getThrowableProxy()).isNull();
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8080", "http://[::1]:8080"})
    void GivenALoopbackAddress_WhenTheStandInExists_ThenItIsServed(String origin) {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(origin + PROBE).exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().actor.get()).isNotNull();
        });
    }

    @Test
    void GivenAnErrorDispatchOfASignedInRequest_WhenItsHandlerRuns_ThenNoHolderIsBound() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(OUTSIDE)
                    .with(request -> {
                        MDC.put(Problems.TRACE_ID, TestActors.TRACE_ID);
                        Caller.build(request, Optional.of(SIGNED_IN), "readTheCaller")
                                .keepFor(request);
                        MDC.clear();
                        request.setDispatcherType(DispatcherType.ERROR);
                        return request;
                    })
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().outside.get()).isEqualTo("unbound");
        });
    }

    @Test
    void GivenAnErrorDispatchOfASignedInRequest_WhenItLogs_ThenTheMdcStillNamesTheCallersAccountAndSession() {
        // The trace filter cleared the MDC when the request's own dispatch ended; the error
        // endpoint's one line must still say whose call failed.
        under(StandInProfiles.TEST, fixture -> {
            fixture.mvc()
                    .get()
                    .uri(OUTSIDE)
                    .with(request -> {
                        MDC.put(Problems.TRACE_ID, TestActors.TRACE_ID);
                        Caller.build(request, Optional.of(SIGNED_IN), "readTheCaller")
                                .keepFor(request);
                        MDC.clear();
                        request.setDispatcherType(DispatcherType.ERROR);
                        return request;
                    })
                    .exchange();

            assertThat(fixture.controller().accountInTheMdc.get())
                    .isEqualTo(SIGNED_IN.account().value().toString());
            assertThat(fixture.controller().sessionInTheMdc.get())
                    .isEqualTo(SIGNED_IN.session().value().toString());
        });
    }

    @Test
    void GivenACallerWithNoActor_WhenItsHandlerRuns_ThenOnlyCurrentCallIsBound() {
        // No operation is open yet, so only a caller kept by hand reaches a handler unsigned, as an
        // open operation's will after #199.
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(OUTSIDE)
                    .with(request -> {
                        MDC.put(Problems.TRACE_ID, TestActors.TRACE_ID);
                        Caller.build(request, Optional.empty(), "readOpen").keepFor(request);
                        MDC.clear();
                        return request;
                    })
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().outside.get()).isEqualTo("bound");
            assertThat(fixture.controller().outsideActor.get()).isEqualTo("unbound");
            assertThat(fixture.controller().accountInTheMdc.get()).isNull();
        });
    }

    @Test
    void GivenAnotherCookie_WhenTheStandInExists_ThenItStillServesTheCall() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri(PROBE)
                    .cookie(new Cookie("language", "he"))
                    .exchange();

            assertThat(result).hasStatusOk();
        });
    }

    @Test
    void GivenACorsPreflight_WhenItArrivesWithNoPrincipal_ThenItIsNotRefused() {
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc()
                    .options()
                    .uri(PROBE)
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .exchange();

            // No CORS is configured, so Spring answers the preflight itself, with no CORS headers.
            assertThat(result).hasStatusOk();
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isNull();
            assertThat(fixture.auditLog().refusals).isEmpty();
        });
    }

    @Test
    void GivenAPreflightThatReachesAHandlerMethod_WhenTheInterceptorChecksIt_ThenItPassesWithNoPrincipal()
            throws NoSuchMethodException {
        AccessInterceptor interceptor = new AccessInterceptor(new NeverCalledAuditLog(), new FixedClock());
        MockHttpServletRequest preflight = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), PROBE);
        preflight.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        preflight.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

        assertThat(interceptor.preHandle(preflight, new MockHttpServletResponse(), probeHandler()))
                .isTrue();
        assertThat(Caller.of(preflight)).isEmpty();
    }

    @Test
    void GivenTheSameHandlerMethodAndNoPreflight_WhenTheInterceptorChecksIt_ThenItRefusesTheCall()
            throws NoSuchMethodException {
        AccessInterceptor interceptor = new AccessInterceptor(new RecordingAuditLog(), new FixedClock());
        MDC.put(Problems.TRACE_ID, TestActors.TRACE_ID);
        try {
            assertThatThrownBy(() -> interceptor.preHandle(
                            new MockHttpServletRequest(HttpMethod.GET.name(), PROBE),
                            new MockHttpServletResponse(),
                            probeHandler()))
                    .isInstanceOf(NotSignedIn.class);
        } finally {
            MDC.clear();
        }
    }

    private static HandlerMethod probeHandler() throws NoSuchMethodException {
        return new HandlerMethod(new ProbeController(), ProbeController.class.getDeclaredMethod("readTheCaller"));
    }

    @Test
    void GivenAHandlerOutsideApiV1_WhenItRuns_ThenNoHolderIsBound() {
        withNoStandIn(fixture -> {
            MvcTestResult result = fixture.mvc().get().uri(OUTSIDE).exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().outside.get()).isEqualTo("unbound");
        });
    }

    @Test
    void GivenARaceOnASignedInCall_WhenItIsAudited_ThenItsRecordNamesTheCaller() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .put()
                    .uri(RACE)
                    .header(HttpHeaders.IF_MATCH, "\"3\"")
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
            AccountId dispatcher = TestActors.dispatcher().account();
            assertThat(fixture.auditLog().refusals).singleElement().satisfies(refusal -> {
                assertThat(refusal.errorCode()).isEqualTo(StaleVersion.CODE);
                assertThat(refusal.call().user()).contains(dispatcher);
                assertThat(refusal.call().session()).contains(StandInSession.SESSION);
                assertThat(refusal.call().operation()).isEqualTo("loseARace");
            });
        });
    }

    /** A handler under {@code /api/v1} that reads its call's holders, and one outside it. */
    @RestController
    static class ProbeController {

        final AtomicReference<Actor> actor = new AtomicReference<>();
        final AtomicReference<CallContext> call = new AtomicReference<>();
        final AtomicReference<String> outside = new AtomicReference<>();
        final AtomicReference<String> outsideActor = new AtomicReference<>();
        final AtomicInteger tries = new AtomicInteger();
        final AtomicReference<String> accountInTheMdc = new AtomicReference<>();
        final AtomicReference<String> sessionInTheMdc = new AtomicReference<>();

        @GetMapping(PROBE)
        String readTheCaller() {
            call.set(CurrentCall.get());
            actor.set(CurrentActor.get());
            accountInTheMdc.set(MDC.get(Caller.ACCOUNT_ID));
            return "read";
        }

        @PostMapping(PROBE)
        String changeSomething() {
            call.set(CurrentCall.get());
            actor.set(CurrentActor.get());
            return "changed";
        }

        @RequestMapping(
                path = PROBE,
                method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        String changeSomethingElse() {
            call.set(CurrentCall.get());
            actor.set(CurrentActor.get());
            return "changed";
        }

        @PostMapping(NOTE)
        String sendANote(@Valid @RequestBody Note note) {
            actor.set(CurrentActor.get());
            return note.text();
        }

        @PostMapping(SHORT_NOTE)
        String sendAShortNote(@Valid @RequestBody ShortNote note) {
            return note.text();
        }

        // Its answer is JSON only, so a client that accepts none of it gets a 406 after it ran.
        @PostMapping(ANSWERED)
        Note answerWithANote() {
            actor.set(CurrentActor.get());
            return new Note("answered");
        }

        @GetMapping(COUNTED)
        String readCounted(@RequestParam int count) {
            return String.valueOf(count);
        }

        @GetMapping(RETRIED)
        String loseOnceThenRead() {
            if (tries.incrementAndGet() == 1) {
                throw new StaleVersion("Plan", PLAN);
            }
            actor.set(CurrentActor.get());
            return "retried";
        }

        @PutMapping(RACE)
        String loseARace() {
            throw new StaleVersion("Plan", PLAN);
        }

        @GetMapping(OUTSIDE)
        String readOutsideTheApi() {
            outside.set(isBound(CurrentCall::get) ? "bound" : "unbound");
            outsideActor.set(isBound(CurrentActor::get) ? "bound" : "unbound");
            accountInTheMdc.set(MDC.get(Caller.ACCOUNT_ID));
            sessionInTheMdc.set(MDC.get(Caller.SESSION_ID));
            return "outside";
        }

        private static boolean isBound(Runnable read) {
            try {
                read.run();
                return true;
            } catch (IllegalStateException unbound) {
                return false;
            }
        }
    }

    /** A request body the contract would declare: one required text. */
    record Note(@NotBlank String text) {}

    /** A note short enough that a name is refused, so the refusal's exception quotes it. */
    record ShortNote(@Size(max = SHORT_NOTE_LENGTH) String text) {}

    record Refusal(CallContext call, Instant at, String errorCode) {}

    /** Records every refusal it is given, with its whole context. */
    static final class RecordingAuditLog implements AuditLog {

        final List<Refusal> refusals = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
            throw new AssertionError("no success is recorded here");
        }

        @Override
        public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
            if (failing) {
                throw new IllegalStateException("the audit log failed");
            }
            assertThat(records).isEmpty();
            refusals.add(new Refusal(call, at, errorCode));
        }
    }

    /** An audit log the test expects never to be written to. */
    private static final class NeverCalledAuditLog implements AuditLog {

        @Override
        public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
            throw new AssertionError("a preflight is never audited");
        }

        @Override
        public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
            throw new AssertionError("a preflight is never audited");
        }
    }

    static final class FixedClock implements Clock {

        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 3, 15);
        }
    }
}
