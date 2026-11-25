package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.CapturedLogs;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.coyote.BadRequestException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.boot.servlet.filter.OrderedRequestContextFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class TraceIdFilterTest {

    private static final String TRACEPARENT = "traceparent";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACE_ID_PATH = "$.traceId";
    private static final String SENTINEL = "zq7-trace-filter-sentinel";

    @RegisterExtension
    final CapturedLogs everyLine = CapturedLogs.ofEveryLogger();

    private final TracingController controller = new TracingController();

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new UnexpectedErrorHandler())
            .addFilters(new TraceIdFilter())
            .build());

    @Test
    void GivenATraceparentHeader_WhenAnErrorIsAnswered_ThenTheProblemCarriesItsTraceId() {
        MvcTestResult result = mvc.get()
                .uri("/trace-id-filter-test/failing")
                .header(TRACEPARENT, "00-" + TRACE_ID + "-00f067aa0ba902b7-01")
                .exchange();

        assertThat(result).bodyJson().extractingPath(TRACE_ID_PATH).asString().isEqualTo(TRACE_ID);
    }

    @Test
    void GivenNoTraceparent_WhenAnErrorIsAnswered_ThenTheTraceIdIsValid() {
        MvcTestResult result = mvc.get().uri("/trace-id-filter-test/failing").exchange();

        String traceId = traceIdOf(result);
        assertThatCode(() -> TraceId.of(traceId)).doesNotThrowAnyException();
    }

    @Test
    void GivenATraceparentWithAnAllZeroTraceId_WhenARequestArrives_ThenAFreshNonZeroTraceIdIsMade() {
        String allZero = "0".repeat(TRACE_ID.length());

        MvcTestResult result = mvc.get()
                .uri("/trace-id-filter-test/failing")
                .header(TRACEPARENT, "00-" + allZero + "-00f067aa0ba902b7-01")
                .exchange();

        String traceId = traceIdOf(result);
        assertThat(traceId).isNotEqualTo(allZero);
        assertThatCode(() -> TraceId.of(traceId)).doesNotThrowAnyException();
    }

    @Test
    void GivenARequest_WhenItIsServed_ThenItsHandlerSeesTheTraceIdInTheMdcAndTheMdcIsClearedAfter() {
        mvc.get()
                .uri("/trace-id-filter-test/traced")
                .header(TRACEPARENT, "00-" + TRACE_ID + "-00f067aa0ba902b7-01")
                .exchange();

        assertThat(controller.seen).hasValue(TRACE_ID);
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    @Test
    void GivenAnErrorDispatchOfATracedRequest_WhenFiltered_ThenItKeepsTheFirstPassTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/failing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> seen = new ArrayList<>();
        FilterChain recordTheTraceId = (passed, answered) -> seen.add(MDC.get(Problems.TRACE_ID));

        filter.doFilter(request, response, recordTheTraceId);
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, recordTheTraceId);

        assertThat(seen).hasSize(2).doesNotContainNull();
        assertThat(seen.get(1)).isEqualTo(seen.get(0));
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    @Test
    void GivenACraftedTraceparent_WhenTheRequestFailsAndIsLogged_ThenNoLineCarriesItsText() {
        String crafted = "crafted-by-a-client";

        MvcTestResult result = mvc.get()
                .uri("/trace-id-filter-test/failing")
                .header(TRACEPARENT, "00-" + TRACE_ID + "-00f067aa0ba902b7-01-" + crafted)
                .exchange();

        assertThat(traceIdOf(result)).isNotEqualTo(TRACE_ID);
        assertThat(everyLine.events())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(CapturedLogs.textsOf(event))
                        .noneMatch(text -> text.contains(crafted) || text.contains(TRACE_ID)));
    }

    @ParameterizedTest
    @EnumSource(
            value = DispatcherType.class,
            names = {"REQUEST", "ASYNC"})
    void GivenAnExceptionThatEscapesTheChain_WhenTheResponseIsOpen_ThenTomcatIsAskedForTheErrorPageWithItsCause(
            DispatcherType dispatch) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/escaping");
        request.setDispatcherType(dispatch);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException escaped = new IllegalStateException("escaped every advice");

        new TraceIdFilter().doFilter(request, response, escaping(wrapped(escaped)));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).isSameAs(escaped);
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    /** Each shape an escaped exception takes, and the exception the error endpoint must see. */
    static Stream<Arguments> escapedShapes() {
        IllegalStateException raw = new IllegalStateException("thrown by a filter");
        IOException io = new IOException("the body could not be read");
        IllegalStateException twiceWrapped = new IllegalStateException("wrapped twice");
        ServletException causeless = new ServletException("no cause");
        return Stream.of(
                Arguments.of(raw, raw),
                Arguments.of(io, io),
                Arguments.of(new ServletException("outer", new ServletException("inner", twiceWrapped)), twiceWrapped),
                Arguments.of(causeless, causeless));
    }

    @Test
    void GivenAPathParameterWithAMalformedEscape_WhenTheRequestArrives_Then400AndTheChainNeverRuns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/health;a=%zz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainRan = new AtomicBoolean();

        new TraceIdFilter().doFilter(request, response, (passed, answered) -> chainRan.set(true));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).isNull();
        assertThat(chainRan).isFalse();
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    @Test
    void GivenAPathParameterWithAValidEscape_WhenTheRequestArrives_ThenTheChainRuns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/health;a=%C3%A9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainRan = new AtomicBoolean();

        new TraceIdFilter().doFilter(request, response, (passed, answered) -> chainRan.set(true));

        assertThat(chainRan).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @ParameterizedTest
    @MethodSource("escapedShapes")
    void GivenEachShapeOfAnEscapedException_WhenFiltered_ThenTheErrorPageGetsTheExceptionUnwrapped(
            Exception escaped, Throwable expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/escaping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, escaping(escaped));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).isSameAs(expected);
    }

    /** Tomcat's quiet refusals, each answered with its own status and nothing that could quote a value. */
    static Stream<Arguments> tomcatsOwnRefusals() {
        return Stream.of(
                Arguments.of(
                        new InvalidParameterException("Parameter [name] with value [" + SENTINEL + "]", 413),
                        HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE),
                Arguments.of(new BadRequestException("bad chunk " + SENTINEL), HttpServletResponse.SC_BAD_REQUEST),
                Arguments.of(
                        wrapped(new InvalidParameterException("Parameter [name] with value [" + SENTINEL + "]")),
                        HttpServletResponse.SC_BAD_REQUEST));
    }

    @ParameterizedTest
    @MethodSource("tomcatsOwnRefusals")
    void GivenTomcatsOwnRefusalEscapingTheChain_WhenFiltered_ThenItsStatusIsAskedWithNoException(
            Exception refused, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/escaping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdFilter().doFilter(request, response, escaping(refused));

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).isNull();
    }

    @Test
    void
            GivenAnExceptionThatEscapesTheChainAfterTheResponseIsCommitted_WhenFiltered_ThenItIsRethrownForTomcatToAbort() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trace-id-filter-test/escaping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);
        IllegalStateException escaped = new IllegalStateException("escaped every advice");

        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, response, escaping(wrapped(escaped))))
                .isInstanceOf(ServletException.class)
                .hasCause(escaped);
        assertThat(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION)).isNull();
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = DispatcherType.class,
            names = {"ERROR", "INCLUDE", "FORWARD"})
    void GivenAnExceptionThatEscapesADispatchInsideTheErrorPage_WhenFiltered_ThenItIsRethrownAndNoOtherIsAsked(
            DispatcherType dispatch) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(dispatch);
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException escaped = new IllegalStateException("the error endpoint failed");

        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, response, escaping(wrapped(escaped))))
                .isInstanceOf(ServletException.class)
                .hasCause(escaped);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(MDC.get(Problems.TRACE_ID)).isNull();
    }

    /** What Spring's servlet throws when an exception escapes every handler. */
    private static ServletException wrapped(Exception escaped) {
        return new ServletException("Request processing failed: " + escaped, escaped);
    }

    /** A chain that throws the given exception, as a later filter or the servlet would. */
    private static FilterChain escaping(Exception escaped) {
        return (passed, answered) -> {
            switch (escaped) {
                case ServletException servlet -> throw servlet;
                case IOException io -> throw io;
                case RuntimeException runtime -> throw runtime;
                default -> throw new AssertionError("a chain throws no other exception", escaped);
            }
        };
    }

    @Test
    void GivenBootsRequestContextFilter_WhenTheirOrdersAreCompared_ThenTheTraceFilterRunsFirst() {
        assertThat(TraceIdFilter.ORDER).isLessThan(new OrderedRequestContextFilter().getOrder());
    }

    private static String traceIdOf(MvcTestResult result) {
        return JsonPath.read(contentOf(result), TRACE_ID_PATH);
    }

    private static String contentOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException unreadable) {
            throw new AssertionError(unreadable);
        }
    }

    @RestController
    static class TracingController {

        final AtomicReference<String> seen = new AtomicReference<>();

        @GetMapping("/trace-id-filter-test/failing")
        String fail() {
            throw new IllegalStateException("connection to db-host:5432 refused");
        }

        @GetMapping("/trace-id-filter-test/traced")
        String traced() {
            seen.set(MDC.get(Problems.TRACE_ID));
            return "traced";
        }
    }
}
