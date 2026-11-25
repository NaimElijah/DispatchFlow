package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.TomcatIntegrationTest;
import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
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
@Import({WebOverTomcatIT.EscapingController.class, WebOverTomcatIT.ParameterReadingFilter.class})
@ExtendWith(OutputCaptureExtension.class)
class WebOverTomcatIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // A value that occurs in no other line, the startup log's user name and paths included.
    private static final String SENTINEL = "zq7WebOverTomcatSentinel";
    // The line Tomcat writes, with no trace ID, when an exception escapes the servlet.
    private static final String TOMCATS_OWN_LINE = "Servlet.service()";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @AfterEach
    void closeTheClient() {
        client.close();
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

    /** The captured lines that mention a text, each read as the one JSON object it must be. */
    private List<JsonNode> linesMentioning(CapturedOutput output, String text) {
        return output.getOut()
                .lines()
                .filter(line -> line.contains(text))
                .map(json::readTree)
                .toList();
    }

    /** Exactly one line tells of the escaped exception: at ERROR, as one JSON line with the trace ID. */
    private void theOneErrorLine(CapturedOutput output) {
        assertThat(linesMentioning(output, EscapedEveryAdvice.class.getName()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID);
                    assertThat(line.toString()).contains("\"ERROR\"");
                });
        assertThat(output.getOut()).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAQueryParameterThatIsNoUtf8_WhenTheHealthProbeReadsIt_Then400ForTheWholeRequestAndNoValueLogged(
            CapturedOutput output) throws Exception {
        HttpResponse<String> response =
                send(to("/actuator/health?name=" + SENTINEL + "%E0%E1").GET());

        JsonNode problem = problemOf(response, 400, Problems.INVALID_REQUEST);
        assertThat(problem.path("errors").path(0).path("code").asString()).isEqualTo(WebCodes.INVALID_VALUE);
        assertThat(problem.path("errors").path(0).path("pointer").asString()).isEmpty();
        assertThat(linesMentioning(output, TRACE_ID))
                .singleElement()
                .satisfies(line -> assertThat(line.toString()).contains("\"WARN\""));
        assertThat(output.getOut()).doesNotContain(SENTINEL).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAFormPostOverTomcatsCap_WhenAHandlerReadsItsBody_Then413BodyTooLarge(CapturedOutput output)
            throws Exception {
        String form = "note=" + "a".repeat(BodySizeLimit.MAX_BODY_BYTES);

        HttpResponse<String> response = send(to(EscapingController.FORM)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(BodyPublishers.ofString(form)));

        problemOf(response, 413, WebCodes.BODY_TOO_LARGE);
        // Tomcat's cap refused it, not BodySizeLimit, whose refusal is a BodyTooLarge.
        assertThat(linesMentioning(output, TRACE_ID))
                .singleElement()
                .satisfies(line -> assertThat(line.toString())
                        .contains("\"WARN\"")
                        .contains("\"exception\":\"" + InvalidParameterException.class.getSimpleName() + "\""));
        assertThat(output.getOut()).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAFilterThatReadsAParameterTomcatCannotDecode_WhenTheRequestArrives_Then400AndNoLineCarriesTheValue(
            CapturedOutput output) throws Exception {
        HttpResponse<String> response = send(
                to(ParameterReadingFilter.PATH + "?name=" + SENTINEL + "%E0%E1").GET());

        problemOf(response, 400, Problems.INVALID_REQUEST);
        assertThat(output.getOut()).doesNotContain(SENTINEL).doesNotContain(TOMCATS_OWN_LINE);
    }

    @Test
    void GivenAnExceptionThatEscapesEveryAdvice_WhenTomcatDispatchesIt_ThenA500ProblemAndOneJsonErrorLine(
            CapturedOutput output) throws Exception {
        HttpResponse<String> response = send(to(EscapingController.ESCAPING).GET());

        JsonNode problem = problemOf(response, 500, WebCodes.UNEXPECTED_ERROR);
        assertThat(problem.path("instance").asString()).isEqualTo(EscapingController.ESCAPING);
        theOneErrorLine(output);
    }

    @Test
    void GivenAnAsyncHandlerWhoseExceptionEscapesEveryAdvice_WhenTomcatDispatchesIt_ThenA500ProblemAndOneErrorLine(
            CapturedOutput output) throws Exception {
        HttpResponse<String> response =
                send(to(EscapingController.ESCAPING_LATER).GET());

        problemOf(response, 500, WebCodes.UNEXPECTED_ERROR);
        theOneErrorLine(output);
    }

    /**
     * Once the response is committed its status can't change, so the trace filter leaves the
     * exception to Tomcat, which cuts the connection: the client can't take the begun body for a
     * whole one. The error endpoint's line still carries the trace ID.
     */
    @Test
    void GivenAnExceptionThatEscapesAfterTheResponseIsCommitted_WhenItLeavesTheHandler_ThenTomcatAbortsTheResponse(
            CapturedOutput output) {
        assertThatThrownBy(
                        () -> send(to(EscapingController.ESCAPING_AFTER_COMMIT).GET()))
                .isInstanceOf(IOException.class)
                .isNotInstanceOf(HttpTimeoutException.class)
                .isNotInstanceOf(ConnectException.class);
        assertThat(linesMentioning(output, EscapedEveryAdvice.class.getName()))
                .anySatisfy(line ->
                        assertThat(line.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID));
    }

    /** Handlers that only a real Tomcat serves as a client would see them. */
    @RestController
    static class EscapingController {

        static final String ESCAPING = "/web-over-tomcat-it/escaping";
        static final String ESCAPING_LATER = "/web-over-tomcat-it/escaping-later";
        static final String ESCAPING_AFTER_COMMIT = "/web-over-tomcat-it/escaping-after-commit";
        static final String FORM = "/web-over-tomcat-it/form";
        static final String BEGUN = "begun";

        @GetMapping(ESCAPING)
        String escape() {
            throw escaped();
        }

        @GetMapping(ESCAPING_LATER)
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

        private static EscapedEveryAdvice escaped() {
            return new EscapedEveryAdvice(
                    new HttpMessageNotReadableException("unreadable", new MockHttpInputMessage(new byte[0])));
        }
    }

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
