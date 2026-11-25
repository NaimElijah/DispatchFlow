package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
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
