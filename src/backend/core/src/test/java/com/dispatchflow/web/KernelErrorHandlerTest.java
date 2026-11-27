package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.dispatchflow.CapturedLogs;
import com.dispatchflow.TestActors;
import com.dispatchflow.access.domain.AccessError;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.service.AppendedAuditRecords;
import com.dispatchflow.audit.application.service.AuditLogService;
import com.dispatchflow.liveops.domain.LiveOpsError;
import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.orders.domain.RowNumber;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.RuleViolations;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class KernelErrorHandlerTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final String VERSION_TAG = "\"3\"";
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2026, 3, 15));
    private static final Instant AT = Instant.parse("2026-03-15T08:00:00Z");

    @RegisterExtension
    final CapturedLogs logs = CapturedLogs.of(KernelErrorHandler.class);

    @RegisterExtension
    final CapturedLogs everyLine = CapturedLogs.ofEveryLogger();

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new KernelErrorHandler(), new UnexpectedErrorHandler())
            .addFilters(new TraceIdFilter())
            .build());

    private MvcTestResult get(String failure) {
        return mvc.get().uri("/kernel-error-handler-test/{failure}", failure).exchange();
    }

    private MvcTestResult getWithIfMatch(String failure) {
        return mvc.get()
                .uri("/kernel-error-handler-test/{failure}", failure)
                .header(HttpHeaders.IF_MATCH, VERSION_TAG)
                .exchange();
    }

    @Test
    void GivenARuleViolation_WhenTheHandlerThrowsIt_ThenAProblemWithItsCodeAndParams() {
        MvcTestResult result = get("date-in-past");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("DATE_IN_PAST");
        assertThat(result).bodyJson().extractingPath("$.params.date").asString().isEqualTo("2026-03-15");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().hasSize(32);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("about:blank");
    }

    @Test
    void GivenRuleViolations_WhenThrown_ThenEveryErrorIsListed() {
        MvcTestResult result = get("import-has-errors");

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("IMPORT_HAS_ERRORS");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.params.rejectedRows")
                .asNumber()
                .isEqualTo(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].code")
                .asArray()
                .containsExactly("ROW_REFUSED", "ROW_REFUSED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].params.row")
                .asArray()
                .containsExactly(2, 5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[1].params.reason.code")
                .asString()
                .isEqualTo("DATE_IN_PAST");
    }

    @Test
    void GivenNotFound_WhenThrown_Then404WithKindAndId() {
        MvcTestResult result = get("not-found");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(NotFound.CODE);
        assertThat(result).bodyJson().extractingPath("$.params.kind").asString().isEqualTo("Plan");
        assertThat(result).bodyJson().extractingPath("$.params.id").asString().isEqualTo(ID.toString());
    }

    @Test
    void GivenAStaleVersionThatComparedTheHeadersVersion_WhenThrown_Then412() {
        MvcTestResult result = getWithIfMatch("require-current");

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(StaleVersion.CODE);
        assertThat(result).bodyJson().extractingPath("$.params.kind").asString().isEqualTo("Plan");
        assertThat(result).bodyJson().extractingPath("$.params.id").asString().isEqualTo(ID.toString());
    }

    @Test
    void GivenAStaleVersionThatComparedABodyVersion_WhenThrown_Then409() {
        MvcTestResult result = get("require-current");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(StaleVersion.CODE);
        assertThat(result).bodyJson().extractingPath("$.params.id").asString().isEqualTo(ID.toString());
    }

    @Test
    void GivenASavesStaleVersionOnACallWithNoVersion_WhenItReachesTheHandler_Then503WithRetryAfter() {
        MvcTestResult result = get("save-conflict");

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.TRY_AGAIN_LATER);
        assertThat(result).headers().hasValue(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring-lock", "jpa-lock", "jpa-lock-timeout"})
    void GivenEachLockWaitType_WhenThrown_Then503WithRetryAfter(String failure) {
        MvcTestResult result = getWithIfMatch(failure);

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.TRY_AGAIN_LATER);
        assertThat(result).headers().hasValue(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS));
    }

    @Test
    void GivenARaceLostOnEveryTry_WhenItIsAnswered_ThenItsOneLineIsAtWarnWithItsCode() {
        get("save-conflict");

        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(CapturedLogs.keyValues(event)).containsEntry("code", WebCodes.TRY_AGAIN_LATER);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"step-up-required", "not-your-route", "stop-not-on-your-route"})
    void GivenAnOverrideCode_WhenARuleViolationCarriesIt_Then403(String failure) {
        assertThat(get(failure)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void GivenRuleViolationsSummarisedByAForbiddenCode_WhenThrown_Then403WithTheSummarysCode() {
        MvcTestResult result = get("step-up-required-with-reasons");

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("STEP_UP_REQUIRED");
    }

    @Test
    void GivenAStaleVersionOnACallThatSentIfNoneMatch_WhenThrown_Then412() {
        MvcTestResult result = mvc.get()
                .uri("/kernel-error-handler-test/{failure}", "save-conflict")
                .header(HttpHeaders.IF_NONE_MATCH, "*")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(StaleVersion.CODE);
    }

    @Test
    void GivenARuleViolation_WhenItIsAnswered_ThenItIsLoggedAtInfoWithItsCodeAndNoValue() {
        get("date-in-past");

        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(CapturedLogs.keyValues(event)).containsEntry("code", "DATE_IN_PAST");
        });
        assertThat(everyLine.events())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(CapturedLogs.textsOf(event))
                        .noneMatch(text -> text.contains(DATE.value().toString())));
    }

    @Test
    void GivenRefreshRefused_WhenThrown_Then401WithExactlyTheBearerChallengeAndEmptyParams() {
        MvcTestResult result = get("refresh-refused");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("REFRESH_REFUSED");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
    }

    @Test
    void GivenRuleViolationsSummarisedByAnUnauthorizedCode_WhenThrown_Then401WithTheBearerChallenge() {
        MvcTestResult result = get("refresh-refused-with-reasons");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
    }

    @Test
    void GivenARuleViolationSessionRevoked_WhenThrown_ThenTheOne401CodeWithEmptyParams() {
        MvcTestResult result = get("session-revoked");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("ACCESS_TOKEN_REFUSED");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
    }

    @Test
    void GivenRuleViolationsSummarisedBySessionRevoked_WhenThrown_ThenTheOne401CodeWithNoErrorsListed() {
        MvcTestResult result = get("session-revoked-with-reasons");

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("ACCESS_TOKEN_REFUSED");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
        assertThat(result).bodyJson().doesNotHavePath("$.errors");
    }

    @Test
    void GivenSessionRevoked_WhenItIsAnswered_ThenTheLogLineKeepsTheSpecificCode() {
        get("session-revoked");

        assertThat(logs.events())
                .singleElement()
                .satisfies(event -> assertThat(CapturedLogs.keyValues(event))
                        .containsEntry("status", 401)
                        .containsEntry("code", "SESSION_REVOKED"));
    }

    @Test
    void GivenAStaleVersionCausedByALockWait_WhenACallWithAVersionThrowsIt_Then412() {
        assertThat(getWithIfMatch("stale-after-lock-wait")).hasStatus(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void GivenALockWaitWrappedInAnotherException_WhenThrown_Then500() {
        MvcTestResult result = get("wrapped-lock");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
        assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNull();
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getCause().getClassName())
                    .isEqualTo(PessimisticLockException.class.getName());
        });
    }

    // Spring matches a handler by the thrown type and then by each of its causes, so a refusal that
    // is only the cause of the thrown exception reached the refusal's handler and answered 422.
    @ParameterizedTest
    @CsvSource({
        "wrapped-rule-violation, com.dispatchflow.shared.RuleViolation",
        "wrapped-rule-violations, com.dispatchflow.shared.RuleViolations",
        "wrapped-not-found, com.dispatchflow.shared.application.NotFound"
    })
    void GivenARefusalWrappedInAnotherException_WhenThrown_Then500AtErrorNotTheRefusalsCode(
            String failure, String wrapped) {
        MvcTestResult result = get(failure);

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getCause().getClassName()).isEqualTo(wrapped);
        });
    }

    @Test
    void GivenAuditRefusingAPartTheServerBuilt_WhenItLeavesTheHandler_Then500AtErrorNotAuditsCode() {
        MvcTestResult result = get("audit-refuses-a-part");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
        assertThat(logs.events())
                .singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void GivenStoredStateInvalid_WhenThrown_Then500WithNoCauseInTheBody() throws Exception {
        MvcTestResult result = get("stored-state-invalid");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(ThrowingController.BROKEN_RULE)
                .doesNotContain(ThrowingController.class.getSimpleName());
    }

    @Test
    void GivenStoredStateInvalid_WhenThrown_ThenOneErrorLineNamesItsSubjectAndTheBodyNone() throws Exception {
        MvcTestResult result = get("stored-state-invalid");

        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(CapturedLogs.keyValues(event))
                    .containsEntry("subject", ThrowingController.class.getSimpleName())
                    .containsEntry("detail", ThrowingController.BROKEN_RULE);
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(StoredStateInvalid.class.getName());
        });
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(ThrowingController.class.getSimpleName())
                .doesNotContain(ThrowingController.BROKEN_RULE);
    }

    @RestController
    static class ThrowingController {

        static final String BROKEN_RULE = "a stop is listed twice";

        @GetMapping("/kernel-error-handler-test/{failure}")
        String fail(@PathVariable String failure) {
            throw switch (failure) {
                case "date-in-past" -> new RuleViolation(new OrdersError.DateInPast(DATE));
                case "import-has-errors" -> violations();
                case "not-found" -> new NotFound("Plan", ID);
                case "require-current" ->
                    thrown(() -> StaleVersion.requireCurrent("Plan", ID, Version.of(4), Version.of(3)));
                case "save-conflict" -> new StaleVersion("Plan", ID, new IllegalStateException("optimistic lock"));
                case "spring-lock" -> new PessimisticLockingFailureException("lock wait");
                case "jpa-lock" -> new PessimisticLockException("lock wait");
                case "jpa-lock-timeout" -> new LockTimeoutException("lock wait");
                case "step-up-required" -> new RuleViolation(new AccessError.StepUpRequired());
                case "not-your-route" -> new RuleViolation(new LiveOpsError.NotYourRoute(RouteId.of(ID)));
                case "stop-not-on-your-route" ->
                    new RuleViolation(new LiveOpsError.StopNotOnYourRoute(StopId.of(ID), RouteId.of(ID)));
                case "refresh-refused" -> new RuleViolation(new AccessError.RefreshRefused());
                case "session-revoked" -> new RuleViolation(new AccessError.SessionRevoked());
                case "session-revoked-with-reasons" ->
                    thrown(() -> RuleViolations.throwIfAny(
                            new AccessError.SessionRevoked(), List.of(new AccessError.SessionRevoked())));
                case "refresh-refused-with-reasons" ->
                    thrown(() -> RuleViolations.throwIfAny(
                            new AccessError.RefreshRefused(), List.of(new AccessError.SessionRevoked())));
                case "step-up-required-with-reasons" ->
                    thrown(() -> RuleViolations.throwIfAny(
                            new AccessError.StepUpRequired(), List.of(new AccessError.SessionRevoked())));
                case "stale-after-lock-wait" -> new StaleVersion("Plan", ID, new PessimisticLockException("lock wait"));
                case "wrapped-lock" -> new IllegalStateException("wrapped", new PessimisticLockException("lock wait"));
                case "stored-state-invalid" -> new StoredStateInvalid(ThrowingController.class, BROKEN_RULE);
                case "wrapped-rule-violation" ->
                    new IllegalStateException("a bug", new RuleViolation(new OrdersError.DateInPast(DATE)));
                case "wrapped-rule-violations" -> new IllegalStateException("a bug", violations());
                case "wrapped-not-found" -> new IllegalStateException("a bug", new NotFound("Plan", ID));
                case "audit-refuses-a-part" ->
                    thrown(() -> new AuditLogService(new AppendedAuditRecords())
                            .refused(
                                    CallContext.serverStep("kernelErrorHandlerTest", TestActors.TRACE_ID),
                                    AT,
                                    List.of(),
                                    "not upper snake case"));
                default -> new IllegalArgumentException("no such failure");
            };
        }

        private static RuntimeException violations() {
            return thrown(() -> RuleViolations.throwIfAny(
                    new OrdersError.ImportHasErrors(2),
                    List.of(
                            new OrdersError.RowRefused(new RowNumber(2), new OrdersError.DateInPast(DATE)),
                            new OrdersError.RowRefused(new RowNumber(5), new OrdersError.DateInPast(DATE)))));
        }

        private static RuntimeException thrown(Runnable throwing) {
            try {
                throwing.run();
            } catch (RuntimeException expected) {
                return expected;
            }
            throw new AssertionError("nothing was thrown");
        }
    }
}
