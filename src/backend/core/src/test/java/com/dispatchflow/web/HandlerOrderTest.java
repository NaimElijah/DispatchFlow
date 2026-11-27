package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.shared.adapter.in.web.HandlerOrder;
import com.dispatchflow.shared.adapter.in.web.Problems;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The four handlers, and a module's, in the order {@link HandlerOrder} gives them. */
class HandlerOrderTest {

    private static final String OPTIMIZER_DOWN = "OPTIMIZER_DOWN";

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new PlanningController())
            .setControllerAdvice(
                    new UnexpectedErrorHandler(),
                    new KernelErrorHandler(),
                    new ContractErrorHandler(new NeverWrittenAuditLog(), new CallRetryTest.FixedClock()),
                    new BodySizeLimit(),
                    new PlanningHandler())
            .addFilters(new TraceIdFilter())
            .build());

    @Test
    void GivenAModulesExceptionWrappingASpringMvcException_WhenThrown_ThenTheModulesHandlerAnswersIt() {
        MvcTestResult result = mvc.get().uri(PlanningController.PATH).exchange();

        assertThat(result.getUnresolvedException()).isNull();
        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(OPTIMIZER_DOWN);
    }

    @RestController
    static class PlanningController {

        static final String PATH = "/handler-order-test/solve";

        @GetMapping(PATH)
        String solve() {
            throw new OptimizerDown(
                    new HttpMessageNotReadableException("unreadable answer", new MockHttpInputMessage(new byte[0])));
        }
    }

    /** A module's own exception, as an optimizer adapter would throw for an answer it can't read. */
    static final class OptimizerDown extends RuntimeException {

        OptimizerDown(Throwable cause) {
            super(cause);
        }
    }

    /** A module's handler, as each module's {@code adapter.in.web} will hold one. */
    @RestControllerAdvice
    @Order(HandlerOrder.MODULE)
    static class PlanningHandler {

        @ExceptionHandler(OptimizerDown.class)
        @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
        ProblemDetail optimizerDown(OptimizerDown down) {
            return Problems.of(HttpStatus.SERVICE_UNAVAILABLE, OPTIMIZER_DOWN);
        }
    }
}
