package com.dispatchflow.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

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
    private static final UUID PLAN = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final Instant NOW = Instant.parse("2026-03-15T08:00:00Z");
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
            .withBean(AuditLog.class, RecordingAuditLog::new)
            .withBean(ProbeController.class);

    /** Every bean of {@code web} and {@code web.security}, but none of the tests' own classes. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.dispatchflow.web",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*(Test|IT)(\\$.*)?"))
    static class WebPackage {}

    private record Fixture(AssertableWebApplicationContext context) {

        MockMvcTester mvc() {
            Filter[] filters =
                    context.getBeanProvider(Filter.class).orderedStream().toArray(Filter[]::new);
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

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
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
    void GivenASameSiteRequestFromTheWebApp_WhenTheStandInExists_ThenItIsServed() {
        under(StandInProfiles.TEST, fixture -> {
            MvcTestResult result = fixture.mvc()
                    .get()
                    .uri("http://localhost:8080" + PROBE)
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header("Sec-Fetch-Site", "same-site")
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(fixture.controller().actor.get()).isNotNull();
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
        AccessInterceptor interceptor = new AccessInterceptor(
                () -> {
                    throw new AssertionError("a preflight is never audited");
                },
                new FixedClock());
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
        AccessInterceptor interceptor = new AccessInterceptor(RecordingAuditLog::new, new FixedClock());
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
        final AtomicInteger tries = new AtomicInteger();
        final AtomicReference<String> accountInTheMdc = new AtomicReference<>();

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
            outside.set(isBound() ? "bound" : "unbound");
            return "outside";
        }

        private static boolean isBound() {
            try {
                CurrentCall.get();
                return true;
            } catch (IllegalStateException unbound) {
                return false;
            }
        }
    }

    /** A request body the contract would declare: one required text. */
    record Note(@NotBlank String text) {}

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
