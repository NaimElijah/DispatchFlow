package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.adapter.in.web.Problems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class UnexpectedErrorHandlerTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new UnexpectedErrorHandler())
            .build());

    @Test
    void GivenAHandlerThatThrows_WhenCalled_ThenAProblemThatKeepsTheCauseOut() throws Exception {
        MvcTestResult result = mvc.get().uri("/failing").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.title").asString().isEqualTo("Internal Server Error");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(FailingController.SECRET);
    }

    @Test
    void GivenAnUnexpectedError_WhenItIsAnswered_ThenTheProblemHasACodeAndTheTraceId() {
        MDC.put(Problems.TRACE_ID, TRACE_ID);

        MvcTestResult result = mvc.get().uri("/failing").exchange();

        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo("UNEXPECTED_ERROR");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isEqualTo(TRACE_ID);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo("about:blank");
        assertThat(result).bodyJson().extractingPath("$.params").asMap().isEmpty();
    }

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @RestController
    static class FailingController {

        static final String SECRET = "connection to db-host:5432 refused";

        @GetMapping("/failing")
        String fail() {
            throw new IllegalStateException(SECRET);
        }
    }
}
