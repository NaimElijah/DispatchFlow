package com.dispatchflow.shared.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.shared.DeliveryDate;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProblemItemTest {

    // Each letter outside the Basic Multilingual Plane is two chars but one code point.
    private static final String WIDE_LETTER = "𝒜";

    @Test
    void GivenAPointerOverTheLimitInCharsButNotInCodePoints_WhenAnItemIsMade_ThenItIsKept() {
        String pointer = "/" + WIDE_LETTER.repeat(100) + "/" + WIDE_LETTER.repeat(20);

        assertThat(ProblemItem.pointer("UNKNOWN_PROPERTY", pointer).pointer()).isEqualTo(pointer);
    }

    @Test
    void GivenAPointerOverTheLimitInCodePoints_WhenAnItemIsMade_ThenItIsCutAtItsLastSlashCountedInCodePoints() {
        String ancestor = "/" + WIDE_LETTER.repeat(100);

        String pointer = ProblemItem.pointer("UNKNOWN_PROPERTY", ancestor + "/" + WIDE_LETTER.repeat(50))
                .pointer();

        assertThat(pointer).isEqualTo(ancestor);
    }

    @Test
    void GivenAnEscapeThatTheLimitWouldSplit_WhenAnItemIsMade_ThenThePointerEndsAtTheLastWholeToken() {
        // The tilde is code point 128, the last one the limit keeps, and its escape's digit the first
        // one past it.
        String ancestor = "/" + "a".repeat(Problems.MAX_POINTER_LENGTH - 3);
        String pointer = ancestor + "/~1b/c";

        assertThat(pointer.charAt(Problems.MAX_POINTER_LENGTH - 1)).isEqualTo('~');
        assertThat(ProblemItem.pointer("UNKNOWN_PROPERTY", pointer).pointer()).isEqualTo(ancestor);
    }

    @Test
    void GivenALongTokenUnderAShortOne_WhenAnItemIsMade_ThenItIsCutToTheShortOne() {
        String pointer = "/stops/" + "1".repeat(Problems.MAX_POINTER_LENGTH * 2);

        assertThat(ProblemItem.pointer("UNKNOWN_PROPERTY", pointer).pointer()).isEqualTo("/stops");
    }

    @Test
    void GivenOneTokenLongerThanTheLimit_WhenAnItemIsMade_ThenItNamesTheWholeBody() {
        String pointer = "/" + "p".repeat(Problems.MAX_POINTER_LENGTH * 2);

        assertThat(ProblemItem.pointer("UNKNOWN_PROPERTY", pointer).pointer()).isEmpty();
    }

    @Test
    void GivenAPointerWithASlashJustPastTheLimit_WhenAnItemIsMade_ThenItKeepsEveryCodePointTheLimitAllows() {
        String ancestor = "/" + "a".repeat(Problems.MAX_POINTER_LENGTH - 1);

        assertThat(ProblemItem.pointer("UNKNOWN_PROPERTY", ancestor + "/b").pointer())
                .isEqualTo(ancestor);
    }

    @Test
    void GivenALongPointer_WhenTheRecordIsBuiltDirectly_ThenItsConstructorCutsIt() {
        ProblemItem item = new ProblemItem("UNKNOWN_PROPERTY", "/stops/" + "1".repeat(200), null, Map.of());

        assertThat(item.pointer()).isEqualTo("/stops");
    }

    @Test
    void GivenBothAPointerAndAParameter_WhenTheRecordIsBuilt_ThenIllegalArgumentException() {
        ProblemItem.Parameter parameter = new ProblemItem.Parameter("id", ProblemItem.Place.PATH);

        assertThatThrownBy(() -> new ProblemItem("WRONG_TYPE", "/id", parameter, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void GivenAPointerWithinTheLimit_WhenAnItemIsMade_ThenItIsKept() {
        assertThat(ProblemItem.pointer("REQUIRED", "/stops/0/name").pointer()).isEqualTo("/stops/0/name");
    }

    @Test
    void GivenAParameter_WhenAnItemIsMade_ThenItsPlaceIsRenderedInLowerCase() {
        assertThat(ProblemItem.parameter("WRONG_TYPE", "planId", ProblemItem.Place.PATH)
                        .asMap())
                .containsEntry("parameter", Map.of("name", "planId", "in", "path"))
                .doesNotContainKey("pointer");
    }

    @Test
    void GivenADomainError_WhenARefusalItemIsMade_ThenItHasTheErrorsCodeAndParamsAndNoPlace() {
        ProblemItem item = ProblemItem.refusal(new OrdersError.DateInPast(DeliveryDate.of(LocalDate.of(2026, 3, 15))));

        assertThat(item.asMap()).isEqualTo(Map.of("code", "DATE_IN_PAST", "params", Map.of("date", "2026-03-15")));
    }

    @Test
    void GivenTheRequestAsAWhole_WhenAnItemIsMadeForIt_ThenItHasNeitherAPointerNorAParameter() {
        ProblemItem item = ProblemItem.request("INVALID_VALUE");

        assertThat(item.asMap()).isEqualTo(Map.of("code", "INVALID_VALUE", "params", Map.of()));
    }
}
