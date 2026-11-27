package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.dispatchflow.CapturedLogs;
import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The error endpoint as Tomcat reaches it: MockMvc never dispatches to the error path, so each test
 * sends the request attributes that Tomcat sets on an error dispatch.
 */
class ErrorEndpointTest {

    private static final String ERROR_PATH = "/error";
    private static final String FAILED_PATH = "/api/v1/plans/0190a5e2-7c3b-7def-8a12-3456789abcde";
    private static final String SECRET = "connection to db-host:5432 refused";
    private static final String STATUS = "status";

    @RegisterExtension
    final CapturedLogs logs = CapturedLogs.of(ErrorEndpoint.class);

    private final MockMvcTester mvc =
            MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ErrorEndpoint(), new EscapingController())
                    .setControllerAdvice(
                            new ContractErrorHandler(new NeverWrittenAuditLog(), new CallRetryTest.FixedClock()),
                            new BodySizeLimit(),
                            new KernelErrorHandler(),
                            new UnexpectedErrorHandler())
                    .addFilters(new TraceIdFilter())
                    .build());

    private MvcTestResult errorDispatch(int status, Throwable exception) {
        return mvc.get()
                .uri(ERROR_PATH)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_EXCEPTION, exception)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, FAILED_PATH)
                .exchange();
    }

    private MvcTestResult errorDispatch(int status) {
        return mvc.get()
                .uri(ERROR_PATH)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, FAILED_PATH)
                .exchange();
    }

    private static void isAProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(code);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("about:blank");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().hasSize(32);
        assertThat(result).bodyJson().extractingPath("$.instance").asString().isEqualTo(FAILED_PATH);
        // The value Spring's own download check sets on an unsafe path, so it never runs here.
        assertThat(result).headers().hasValue(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
    }

    /** The endpoint's one line, at a level, with the status it carries. */
    private void loggedOnceAt(Level level, int status) {
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(level);
            assertThat(CapturedLogs.keyValues(event)).containsEntry(STATUS, status);
        });
    }

    @Test
    void GivenAnErrorDispatchOfA500WithAnException_WhenTheEndpointAnswers_ThenAProblemAndAnErrorLineWithTheException()
            throws Exception {
        MvcTestResult result = errorDispatch(500, new IllegalStateException(SECRET));

        isAProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET);
        loggedOnceAt(Level.ERROR, 500);
        assertThat(logs.events().getFirst().getThrowableProxy().getClassName())
                .isEqualTo(IllegalStateException.class.getName());
    }

    @Test
    void GivenAnErrorDispatchOfA404WithoutAnException_WhenTheEndpointAnswers_ThenNoSuchOperationLoggedAtInfo() {
        MvcTestResult result = errorDispatch(404);

        isAProblem(result, HttpStatus.NOT_FOUND, WebCodes.NO_SUCH_OPERATION);
        loggedOnceAt(Level.INFO, 404);
    }

    @Test
    void GivenAFailedPathWithABracket_WhenTheEndpointAnswers_ThenTheInstanceIsThePathWithItsBracketsQuoted() {
        MvcTestResult result = mvc.get()
                .uri(ERROR_PATH)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/plans/caf%C3%A9[1]")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NO_SUCH_OPERATION);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.instance")
                .asString()
                .isEqualTo("/api/v1/plans/caf%C3%A9%5B1%5D");
        loggedOnceAt(Level.INFO, 404);
    }

    @Test
    void GivenAnErrorDispatchOfA400_WhenTheEndpointAnswers_ThenItListsTheWholeRequestAsItsOneItem() {
        MvcTestResult result = errorDispatch(400);

        isAProblem(result, HttpStatus.BAD_REQUEST, Problems.INVALID_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.INVALID_VALUE);
        assertThat(result).bodyJson().doesNotHavePath("$.errors[0].pointer").doesNotHavePath("$.errors[0].parameter");
    }

    @Test
    void GivenAnErrorDispatchOfA503_WhenTheEndpointAnswers_ThenTryAgainLaterWithRetryAfter() {
        MvcTestResult result = errorDispatch(503);

        isAProblem(result, HttpStatus.SERVICE_UNAVAILABLE, WebCodes.TRY_AGAIN_LATER);
        assertThat(result).headers().hasValue(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS));
    }

    @Test
    void GivenAnErrorDispatchOfA401_WhenTheEndpointAnswers_ThenTheOne401CodeAndExactlyTheBearerChallenge() {
        MvcTestResult result = errorDispatch(401);

        isAProblem(result, HttpStatus.UNAUTHORIZED, WebCodes.ACCESS_TOKEN_REFUSED);
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
        assertThat(result.getResponse().getHeaders(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
    }

    @Test
    void GivenAnErrorDispatchOfAStatusTheContractDoesNotList_WhenTheEndpointAnswers_Then500LoggedAtError() {
        MvcTestResult result = errorDispatch(HttpStatus.GONE.value());

        isAProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR);
        loggedOnceAt(Level.ERROR, HttpStatus.GONE.value());
    }

    @Test
    void GivenARequestForTheErrorPathItself_WhenItArrives_Then404NoSuchOperationLoggedAtInfo() {
        MvcTestResult result = mvc.get().uri(ERROR_PATH).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NO_SUCH_OPERATION);
        loggedOnceAt(Level.INFO, 404);
    }

    @Test
    void GivenAnExceptionWrappingASpringMvcException_WhenNoAdviceAnswers_ThenTheErrorEndpointAnswersAProblem() {
        MvcTestResult escaped = mvc.get().uri(EscapingController.PATH).exchange();

        // The trace filter hands what escaped Spring MVC, unwrapped, to Tomcat's error page, which
        // MockMvc never dispatches to.
        assertThat(escaped).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(escaped.getUnresolvedException()).isNull();
        Object handedOver = escaped.getRequest().getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        assertThat(handedOver).isInstanceOf(EscapingController.Escaped.class);

        MvcTestResult result = errorDispatch(500, (Throwable) handedOver);

        isAProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR);
        loggedOnceAt(Level.ERROR, 500);
    }

    @RestController
    static class EscapingController {

        static final String PATH = "/error-endpoint-test/escaping";

        @GetMapping(PATH)
        String escape() {
            // The contract's handler matches the cause, rethrows the type it doesn't know, and no
            // later advice is asked.
            throw new Escaped(new HttpMessageNotReadableException("unreadable", new MockHttpInputMessage(new byte[0])));
        }

        /** An exception of no advice's, wrapping one of Spring MVC's. */
        static final class Escaped extends RuntimeException {

            Escaped(Throwable cause) {
                super(cause);
            }
        }
    }
}
