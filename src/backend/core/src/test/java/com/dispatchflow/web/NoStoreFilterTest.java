package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class NoStoreFilterTest {

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new UnexpectedErrorHandler())
            .addFilters(new NoStoreFilter())
            .build());

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/probe", "/api/v1/failing-probe", "/api/v1/no-such-route", "/api/v1"})
    void GivenAnyAnswerUnderApiV1_WhenItIsWritten_ThenItIsNoStoreNoTransform(String path) {
        MvcTestResult result = mvc.get().uri(path).exchange();

        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store, no-transform");
    }

    @Test
    void GivenAFailingHandlerUnderApiV1_WhenItIsCalled_ThenTheRefusalIsNoStoreNoTransformToo() {
        MvcTestResult result = mvc.get().uri("/api/v1/failing-probe").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store, no-transform");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/outside/probe", "/api/v10/probe", "/api/v1probe"})
    void GivenAPathOutsideApiV1_WhenItIsAnswered_ThenItsCacheControlIsLeftAlone(String path) {
        MvcTestResult result = mvc.get().uri(path).exchange();

        assertThat(result).headers().doesNotContainHeader(HttpHeaders.CACHE_CONTROL);
    }

    @RestController
    static class ProbeController {

        @GetMapping({"/api/v1/probe", "/outside/probe", "/api/v10/probe", "/api/v1probe", "/api/v1"})
        String probe() {
            return "probe";
        }

        @GetMapping("/api/v1/failing-probe")
        String failingProbe() {
            throw new IllegalStateException("probe failure");
        }
    }
}
