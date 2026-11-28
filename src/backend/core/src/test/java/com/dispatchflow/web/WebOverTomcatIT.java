package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.TestActors;
import com.dispatchflow.TomcatIntegrationTest;
import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code web}'s answers behind a real Tomcat, for what MockMvc can't show: Tomcat's refusal of a
 * request's parameters, which its {@code getParameter} methods throw, and its error dispatch of an
 * exception that escaped every exception handler, which reaches {@code ErrorEndpoint} only through
 * Tomcat (ADR-142 decision 5).
 */
@TomcatIntegrationTest
@Import({
    WebOverTomcatIT.EscapingController.class,
    WebOverTomcatIT.ParameterReadingFilter.class,
    WebOverTomcatIT.LegacyHandler.class
})
@ExtendWith(OutputCaptureExtension.class)
class WebOverTomcatIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // A value that occurs in no other line, the startup log's user name and paths included.
    private static final String SENTINEL = "zq7WebOverTomcatSentinel";
    // The line Tomcat writes, with no trace ID, when an exception escapes the servlet.
    private static final String TOMCATS_OWN_LINE = "Servlet.service()";
    private static final int STATUS_DIGITS = 3;
    // Where an answer's headers end and its body starts.
    private static final String BLANK_LINE = "\r\n\r\n";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcClient jdbc;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private CapturedOutput output;
    private int outputBefore;

    /**
     * Marks where this test's output starts: the capture also holds what the context's startup and
     * earlier tests wrote, late lines included.
     */
    @BeforeEach
    void markTheOutput(CapturedOutput captured) {
        output = captured;
        outputBefore = captured.getOut().length();
    }

    @AfterEach
    void closeTheClient() {
        client.close();
    }

    /** What the console got since this test began. */
    private String newOutput() {
        return output.getOut().substring(outputBefore);
    }

    /** A log line's level, as Elastic Common Schema nests it. */
    private static String levelOf(JsonNode line) {
        return line.path("log").path("level").asString();
    }

    /** The JSON lines written since this test began, each read as the one object it is. */
    private List<JsonNode> newJsonLines() {
        return newOutput()
                .lines()
                .filter(line -> line.startsWith("{"))
                .map(json::readTree)
                .toList();
    }

    /** The lines written since this test began at ERROR. */
    private List<JsonNode> newErrorLines() {
        return newJsonLines().stream()
                .filter(line -> levelOf(line).equals("ERROR"))
                .toList();
    }

    /**
     * Sends a request target that {@code java.net.http} refuses to send, over a raw socket to this
     * test's own server. HTTP/1.0, so Tomcat closes the connection after one unchunked answer.
     */
    private RawResponse sendRaw(String target) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            String request =
                    "GET " + target + " HTTP/1.0\r\nHost: localhost\r\ntraceparent: " + TRACEPARENT + "\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int statusStart = answer.indexOf(' ') + 1;
            int headersEnd = answer.indexOf(BLANK_LINE);
            return new RawResponse(
                    Integer.parseInt(answer.substring(statusStart, statusStart + STATUS_DIGITS)),
                    answer.substring(0, headersEnd),
                    answer.substring(headersEnd + BLANK_LINE.length()));
        }
    }

    /** A raw answer: its status, its status line and headers as sent, and its body. */
    private record RawResponse(int status, String head, String body) {}

    /**
     * Tomcat refuses a {@code [}, a {@code ]}, a {@code {} or a malformed escape in the path itself,
     * with its own 400 page, before any filter runs, so no problem body and no trace ID can answer
     * it (ADR-142: a connector-level refusal never reaches the application). No line may quote the
     * request target: Tomcat's own line for a request line it can't parse would, at INFO once a day
     * per connection processor, before any filter sets a trace ID.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health[1]", "/actuator/health%zz", "/actuator/health{1}"})
    void GivenAPathTomcatCannotParse_WhenItArrives_ThenTomcatAnswers400AndNoLineQuotesIt(String target)
            throws Exception {
        assertThat(sendRaw(target).status()).isEqualTo(400);
        assertThat(newErrorLines()).isEmpty();
        assertThat(newOutput()).doesNotContain(target);
    }

    /**
     * Tomcat passes a path parameter's escape undecoded, so a malformed one reaches the trace
     * filter, which refuses it before Spring parses the path; the error endpoint answers the 400.
     */
    @Test
    void GivenAPathParameterWithAMalformedEscape_WhenItArrives_Then400ForTheWholeRequestLoggedAtInfoAlone()
            throws Exception {
        RawResponse response = sendRaw("/actuator/health;a=%zz");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.head()).containsIgnoringCase(HttpHeaders.CONTENT_DISPOSITION + ":");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.path("code").asString()).isEqualTo(Problems.INVALID_REQUEST);
        assertThat(problem.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID);
        assertThat(problem.path("instance").asString()).isEqualTo("/actuator/health;a=%25zz");
        assertThat(problem.path("errors")).hasSize(1);
        assertThat(problem.path("errors").path(0).path("code").asString()).isEqualTo(WebCodes.INVALID_VALUE);
        assertThat(problem.path("errors").path(0).has("pointer")).isFalse();
        assertThat(problem.path("errors").path(0).has("parameter")).isFalse();
        List<JsonNode> lines = newJsonLines();
        assertThat(lines)
                .filteredOn(line -> line.path("log").path("logger").asString().equals(ErrorEndpoint.class.getName()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(levelOf(line)).isEqualTo("INFO");
                    assertThat(line.path("status").asInt()).isEqualTo(400);
                });
        assertThat(lines)
                .noneMatch(
                        line -> levelOf(line).equals("ERROR") || levelOf(line).equals("WARN"));
        assertThat(newOutput()).doesNotContain(TOMCATS_OWN_LINE);
    }

    private HttpRequest.Builder to(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
                .header("traceparent", TRACEPARENT)
                .timeout(TIMEOUT);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), BodyHandlers.ofString());
    }

    /** The response's problem, which carries the request's trace ID and no detail. */
    private JsonNode problemOf(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValue(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.path("code").asString()).isEqualTo(code);
        assertThat(problem.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID);
        assertThat(problem.has("detail")).isFalse();
        return problem;
    }

    /** The lines written since this test began that mention a text, each read as the one JSON object it is. */
    private List<JsonNode> linesMentioning(String text) {
        return newOutput()
                .lines()
                .filter(line -> line.contains(text))
                .map(json::readTree)
                .toList();
    }

    /** Exactly one line tells of the escaped exception: at ERROR, as one JSON line with the trace ID. */
    private void theOneErrorLine() {
        assertThat(linesMentioning(EscapedEveryAdvice.class.getName()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID);
                    assertThat(levelOf(line)).isEqualTo("ERROR");
                });
        assertThat(newOutput()).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAQueryParameterThatIsNoUtf8_WhenTheHealthProbeReadsIt_Then400ForTheWholeRequestAndNoValueLogged()
            throws Exception {
        HttpResponse<String> response =
                send(to("/actuator/health?name=" + SENTINEL + "%E0%E1").GET());

        JsonNode problem = problemOf(response, 400, Problems.INVALID_REQUEST);
        assertThat(problem.path("errors").path(0).path("code").asString()).isEqualTo(WebCodes.INVALID_VALUE);
        assertThat(problem.path("errors").path(0).has("pointer")).isFalse();
        assertThat(problem.path("errors").path(0).has("parameter")).isFalse();
        assertThat(response.body()).doesNotContain(SENTINEL);
        assertThat(linesMentioning(TRACE_ID))
                .singleElement()
                .satisfies(line -> assertThat(levelOf(line)).isEqualTo("WARN"));
        assertThat(newOutput()).doesNotContain(SENTINEL).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAFormPostOverTomcatsCap_WhenAHandlerReadsItsBody_Then413BodyTooLarge() throws Exception {
        String form = "note=" + "a".repeat(BodySizeLimit.MAX_BODY_BYTES);

        HttpResponse<String> response = send(to(EscapingController.FORM)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(BodyPublishers.ofString(form)));

        problemOf(response, 413, WebCodes.BODY_TOO_LARGE);
        // Tomcat's cap refused it, not BodySizeLimit, whose refusal is a BodyTooLarge.
        assertThat(linesMentioning(TRACE_ID)).singleElement().satisfies(line -> {
            assertThat(levelOf(line)).isEqualTo("WARN");
            assertThat(line.toString())
                    .contains("\"exception\":\"" + InvalidParameterException.class.getSimpleName() + "\"");
        });
        assertThat(newOutput()).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAFilterThatReadsAParameterTomcatCannotDecode_WhenTheRequestArrives_Then400AndNoLineCarriesTheValue()
            throws Exception {
        HttpResponse<String> response = send(
                to(ParameterReadingFilter.PATH + "?name=" + SENTINEL + "%E0%E1").GET());

        problemOf(response, 400, Problems.INVALID_REQUEST);
        assertThat(response.body()).doesNotContain(SENTINEL);
        assertThat(newOutput()).doesNotContain(SENTINEL).doesNotContain(TOMCATS_OWN_LINE);
    }

    /**
     * Another spelling of {@code /api/v1}, one Spring's API versioning would have read as {@code v1},
     * or one percent-encoded, reaches no operation unchecked: the access check guards all of {@code
     * /api} on the decoded path, so a caller with no principal gets the one 401, never a 500 from a
     * handler that found no caller (ADR-160).
     */
    @ParameterizedTest
    @ValueSource(strings = {"/api/V1/plans", "/api/1/plans", "/api/v1.0/plans", "/api/v01/plans", "/%61pi/v1/plans"})
    void GivenNoPrincipalAndAnotherSpellingOfTheApiPath_WhenOpenPlanIsCalled_ThenItIsRefusedAndNever500(String path)
            throws Exception {
        HttpResponse<String> response = send(to(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(BodyPublishers.ofString("{\"date\":\"9001-01-01\"}")));

        problemOf(response, 401, Problems.ACCESS_TOKEN_REFUSED);
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store, no-transform");
    }

    /**
     * Failsafe exports {@code SERVER_ADDRESS=0.0.0.0} to every integration test, as a developer might
     * for a phone; the annotation's own setting outranks it, so this server still listens on the
     * loopback interface alone (ADR-148 decision 3).
     */
    @Test
    void GivenAnExportedServerAddress_WhenTheTestsServerStarts_ThenItListensOnTheLoopbackAlone() {
        assertThat(environment.getProperty("server.address"))
                .isEqualTo(InetAddress.getLoopbackAddress().getHostAddress());
    }

    /**
     * The trace filter clears the MDC when the request's own dispatch ends; on the error dispatch it
     * puts the signed-in caller back, so the error endpoint's one ERROR line says whose call failed.
     */
    @ParameterizedTest
    @ValueSource(strings = {EscapingController.API_ESCAPING, EscapingController.API_ESCAPING_LATER})
    void GivenASignedInCallWhoseExceptionEscapesEveryAdvice_WhenTomcatDispatchesIt_ThenTheErrorLineNamesTheCaller(
            String path) throws Exception {
        HttpResponse<String> response = send(to(path).GET());

        problemOf(response, 500, WebCodes.UNEXPECTED_ERROR);
        // Set before the chain ran, the header survives Tomcat's dispatch to the error page (ADR-157).
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store, no-transform");
        theOneErrorLine();
        assertThat(linesMentioning(EscapedEveryAdvice.class.getName()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.path("accountId").asString())
                            .isEqualTo(TestActors.dispatcher().account().value().toString());
                    assertThat(line.path("sessionId").asString()).hasSize(36);
                });
    }

    @Test
    void GivenNoPrincipal_WhenAnHttpRequestHandlerUnderApiV1IsCalled_ThenTheOne401AndNoErrorLine() throws Exception {
        // A presented token fails until #199, so the call has no principal.
        HttpResponse<String> response = send(to(LegacyHandler.PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                .POST(BodyPublishers.noBody()));

        JsonNode problem = problemOf(response, 401, Problems.ACCESS_TOKEN_REFUSED);
        assertThat(response.headers().allValues(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly(Problems.BEARER_CHALLENGE);
        assertThat(problem.path("params").isObject()).isTrue();
        assertThat(problem.path("params").isEmpty()).isTrue();
        assertThat(response.body()).doesNotContain(SENTINEL);
        assertThat(newErrorLines()).isEmpty();
        assertThat(newOutput()).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenNoPrincipal_WhenAChangingOperationUnderApiV1IsCalled_ThenThe401AndItsRefusedRecordIsStored()
            throws Exception {
        // Its own trace ID, so the row it reads is this call's alone.
        String trace = "5a0d7c1f2e3b4a6987c6d5e4f3a2b1c0";
        HttpResponse<String> response = send(to(EscapingController.API_NOTE)
                .setHeader("traceparent", "00-" + trace + "-00f067aa0ba902b7-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                .POST(BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(jdbc.sql(
                                "SELECT operation, outcome, error_code, account FROM audit.audit_record WHERE trace_id = :trace")
                        .param("trace", trace)
                        .query((row, index) -> new StoredRefusal(
                                row.getString("operation"),
                                row.getString("outcome"),
                                row.getString("error_code"),
                                Optional.ofNullable(row.getString("account"))))
                        .list())
                .containsExactly(
                        new StoredRefusal("takeANote", "REFUSED", Problems.ACCESS_TOKEN_REFUSED, Optional.empty()));
    }

    @Test
    void GivenAnExceptionThatEscapesEveryAdvice_WhenTomcatDispatchesIt_ThenA500ProblemAndOneJsonErrorLine()
            throws Exception {
        HttpResponse<String> response = send(to(EscapingController.ESCAPING).GET());

        JsonNode problem = problemOf(response, 500, WebCodes.UNEXPECTED_ERROR);
        assertThat(problem.path("instance").asString()).isEqualTo(EscapingController.ESCAPING);
        theOneErrorLine();
    }

    @Test
    void GivenAnAsyncHandlerWhoseExceptionEscapesEveryAdvice_WhenTomcatDispatchesIt_ThenA500ProblemAndOneErrorLine()
            throws Exception {
        HttpResponse<String> response =
                send(to(EscapingController.ESCAPING_LATER).GET());

        problemOf(response, 500, WebCodes.UNEXPECTED_ERROR);
        theOneErrorLine();
    }

    /**
     * Once the response is committed its status can't change, so the trace filter leaves the
     * exception to Tomcat, which cuts the connection: the client can't take the begun body for a
     * whole one. The error endpoint's line still carries the trace ID.
     */
    @Test
    void GivenAnExceptionThatEscapesAfterTheResponseIsCommitted_WhenItLeavesTheHandler_ThenTomcatAbortsTheResponse() {
        assertThatThrownBy(
                        () -> send(to(EscapingController.ESCAPING_AFTER_COMMIT).GET()))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(HttpTimeoutException.class)
                .isNotInstanceOf(ConnectException.class);
        assertThat(linesMentioning(EscapedEveryAdvice.class.getName()))
                .anySatisfy(line ->
                        assertThat(line.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID));
    }

    /** Handlers that only a real Tomcat serves as a client would see them. */
    @RestController
    static class EscapingController {

        static final String ESCAPING = "/web-over-tomcat-it/escaping";
        static final String ESCAPING_LATER = "/web-over-tomcat-it/escaping-later";
        static final String ESCAPING_AFTER_COMMIT = "/web-over-tomcat-it/escaping-after-commit";
        static final String API_ESCAPING = "/api/v1/web-over-tomcat-it/escaping";
        static final String API_ESCAPING_LATER = "/api/v1/web-over-tomcat-it/escaping-later";
        static final String FORM = "/web-over-tomcat-it/form";
        static final String API_NOTE = "/api/v1/web-over-tomcat-it/note";
        static final String BEGUN = "begun";

        @GetMapping({ESCAPING, API_ESCAPING})
        String escape() {
            throw escaped();
        }

        @GetMapping({ESCAPING_LATER, API_ESCAPING_LATER})
        Callable<String> escapeLater() {
            return () -> {
                throw escaped();
            };
        }

        @GetMapping(ESCAPING_AFTER_COMMIT)
        void escapeAfterCommit(HttpServletResponse response) throws IOException {
            response.getWriter().write(BEGUN);
            response.flushBuffer();
            throw escaped();
        }

        @PostMapping(FORM)
        String form(@RequestBody String note) {
            return note;
        }

        @PostMapping(API_NOTE)
        String takeANote() {
            return "taken";
        }

        private static EscapedEveryAdvice escaped() {
            return new EscapedEveryAdvice(
                    new HttpMessageNotReadableException("unreadable", new MockHttpInputMessage(new byte[0])));
        }
    }

    /** A refused call's audit row, as stored. */
    private record StoredRefusal(String operation, String outcome, String errorCode, Optional<String> account) {}

    /**
     * An exception of no advice's, wrapping one of Spring MVC's: the contract's handler matches its
     * cause and rethrows it, and no later advice is asked (ADR-142 decision 4).
     */
    static final class EscapedEveryAdvice extends RuntimeException {

        EscapedEveryAdvice(Throwable cause) {
            super("escaped every advice", cause);
        }
    }

    /**
     * A handler under {@code /api/v1} that is no annotated handler method, which its bean name maps:
     * Spring's advice never sees an exception of its type, so only the refusal's own status answers
     * a call to it with no principal (ADR-148 decision 4).
     */
    @Component(LegacyHandler.PATH)
    static class LegacyHandler implements HttpRequestHandler {

        static final String PATH = "/api/v1/web-over-tomcat-it/legacy";

        @Override
        public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write(SENTINEL);
        }
    }

    /**
     * A later filter that reads a parameter on one path, as Spring Security's CSRF check can, so
     * Tomcat's refusal escapes outside Spring MVC and reaches the trace filter.
     */
    @Order(TraceIdFilter.ORDER + 1)
    static class ParameterReadingFilter extends OncePerRequestFilter {

        static final String PATH = "/web-over-tomcat-it/filter-reads-a-parameter";

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !PATH.equals(request.getRequestURI());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            request.getParameter("x");
            chain.doFilter(request, response);
        }
    }
}
