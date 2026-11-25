package com.dispatchflow.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.dispatchflow.CapturedLogs;
import com.dispatchflow.audit.domain.TraceId;
import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.orders.domain.RowNumber;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.RuleViolations;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemsTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2026, 3, 15));

    @RegisterExtension
    final CapturedLogs logs = CapturedLogs.of(Problems.class);

    @AfterEach
    void clearTheMdc() {
        MDC.clear();
    }

    @Test
    void GivenAStatusAndACode_WhenAProblemIsMade_ThenItIsAboutBlankWithTheStatusPhraseAndEmptyParams() {
        ProblemDetail problem = Problems.of(HttpStatus.NOT_FOUND, "NO_SUCH_OPERATION");

        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getProperties())
                .containsEntry("code", "NO_SUCH_OPERATION")
                .containsEntry("params", Map.of());
    }

    @Test
    void GivenAPathWithBracketsAndAnEscape_WhenAProblemIsPlacedAtIt_ThenOnlyTheBracketsAreQuoted() {
        MDC.put(Problems.TRACE_ID, TRACE);

        ProblemDetail problem =
                Problems.at(Problems.of(HttpStatus.NOT_FOUND, "NO_SUCH_OPERATION"), "/plans/caf%C3%A9[1]");

        assertThat(problem.getInstance()).isEqualTo(URI.create("/plans/caf%C3%A9%5B1%5D"));
        assertThat(logs.events()).isEmpty();
    }

    @Test
    void GivenAPathParameterWithAMalformedEscape_WhenAProblemIsPlacedAtIt_ThenItsPercentIsQuoted() {
        MDC.put(Problems.TRACE_ID, TRACE);

        ProblemDetail problem =
                Problems.at(Problems.of(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"), "/actuator/health;a=%zz");

        assertThat(problem.getInstance()).isEqualTo(URI.create("/actuator/health;a=%25zz"));
        assertThat(logs.events()).isEmpty();
    }

    @Test
    void GivenAValidEscapeBesideAMalformedOne_WhenAProblemIsPlacedAtIt_ThenOnlyTheMalformedPercentIsQuoted() {
        MDC.put(Problems.TRACE_ID, TRACE);

        ProblemDetail problem =
                Problems.at(Problems.of(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"), "/plans/caf%C3%A9;a=%z1%");

        assertThat(problem.getInstance()).isEqualTo(URI.create("/plans/caf%C3%A9;a=%25z1%25"));
    }

    @Test
    void GivenATraceIdInTheMdc_WhenAProblemIsMade_ThenItCarriesIt() {
        MDC.put(Problems.TRACE_ID, TRACE);

        assertThat(Problems.of(HttpStatus.CONFLICT, "STALE_VERSION").getProperties())
                .containsEntry(Problems.TRACE_ID, TRACE);
    }

    @Test
    void GivenNoTraceIdInTheMdc_WhenAProblemIsMade_ThenItCarriesAFreshValidOneAndSaysSoAtWarn() {
        Object traceId = Problems.of(HttpStatus.CONFLICT, "STALE_VERSION")
                .getProperties()
                .get(Problems.TRACE_ID);

        assertThat(traceId)
                .isInstanceOfSatisfying(
                        String.class,
                        fresh -> assertThatCode(() -> TraceId.of(fresh)).doesNotThrowAnyException());
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("outside a traced request");
            assertThat(CapturedLogs.keyValues(event)).containsEntry(Problems.TRACE_ID, traceId);
        });
    }

    @Test
    void GivenMoreItemsThanTheCap_WhenAnInvalidRequestIsMade_ThenItListsTheFirstHundredInOrder() {
        List<ProblemItem> items = IntStream.rangeClosed(0, Problems.MAX_ITEMS)
                .mapToObj(index -> ProblemItem.pointer("REQUIRED", "/lines/" + index))
                .toList();

        List<?> listed =
                (List<?>) Problems.invalidRequest(items).getProperties().get("errors");

        assertThat(Problems.MAX_ITEMS).isEqualTo(100);
        assertThat(listed).hasSize(Problems.MAX_ITEMS);
        assertThat(((Map<?, ?>) listed.getLast()).get("pointer")).isEqualTo("/lines/" + (Problems.MAX_ITEMS - 1));
    }

    @Test
    void GivenADomainErrorNestingAnother_WhenAProblemIsMade_ThenItsCodeAndComponentsAreTheParams() {
        OrdersError.RowRefused refused = new OrdersError.RowRefused(new RowNumber(3), new OrdersError.DateInPast(DATE));

        ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, refused);

        assertThat(problem.getProperties())
                .containsEntry("code", "ROW_REFUSED")
                .containsEntry(
                        "params",
                        Map.of(
                                "row",
                                3,
                                "reason",
                                Map.of("code", "DATE_IN_PAST", "params", Map.of("date", "2026-03-15"))));
    }

    @Test
    void GivenRuleViolations_WhenAProblemIsMade_ThenItCarriesTheSummaryAndListsEveryError() {
        RuleViolations violations = catchViolations(
                new OrdersError.ImportHasErrors(2),
                List.of(
                        new OrdersError.DateInPast(DATE),
                        new OrdersError.RowRefused(new RowNumber(7), new OrdersError.DateInPast(DATE))));

        ProblemDetail problem = Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, violations);

        assertThat(problem.getProperties()).containsEntry("code", "IMPORT_HAS_ERRORS");
        List<Object> errors = List.copyOf((List<?>) problem.getProperties().get("errors"));
        assertThat(errors)
                .extracting(item -> (Object) ((Map<?, ?>) item).get("code"))
                .containsExactly("DATE_IN_PAST", "ROW_REFUSED");
    }

    @Test
    void GivenFailingItems_WhenAnInvalidRequestIsMade_Then400ListingEach() {
        ProblemDetail problem = Problems.invalidRequest(List.of(
                ProblemItem.pointer("REQUIRED", "/name"),
                ProblemItem.parameter("MISSING_PARAMETER", "If-Match", ProblemItem.Place.HEADER)));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "INVALID_REQUEST");
        assertThat(problem.getProperties().get("errors"))
                .isEqualTo(List.of(
                        Map.of("code", "REQUIRED", "pointer", "/name", "params", Map.of()),
                        Map.of(
                                "code",
                                "MISSING_PARAMETER",
                                "parameter",
                                Map.of("name", "If-Match", "in", "header"),
                                "params",
                                Map.of())));
    }

    @Test
    void GivenNoItems_WhenAnInvalidRequestIsMade_ThenIllegalArgumentException() {
        assertThatThrownBy(() -> Problems.invalidRequest(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenARefusedToken_WhenUnauthorizedIsAnswered_Then401WithTheBareBearerChallenge() {
        ResponseEntity<ProblemDetail> answer = Problems.unauthorized("REFRESH_REFUSED");

        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(answer.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).containsExactly("Bearer");
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().getProperties())
                .containsEntry("code", "REFRESH_REFUSED")
                .containsEntry("params", Map.of());
    }

    private static RuleViolations catchViolations(DomainError summary, List<DomainError> errors) {
        try {
            RuleViolations.throwIfAny(summary, errors);
        } catch (RuleViolations thrown) {
            return thrown;
        }
        throw new AssertionError("RuleViolations.throwIfAny threw nothing");
    }
}
