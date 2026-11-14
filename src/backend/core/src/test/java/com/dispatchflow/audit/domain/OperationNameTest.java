package com.dispatchflow.audit.domain;

import static com.dispatchflow.shared.DomainAssertions.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.domain.AuditError.MalformedOperationName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ADR-110: an audit record's operation is an ASCII identifier, never free text (NFR-93, NFR-94). */
class OperationNameTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "publishPlan",
                "signIn",
                "a",
                "Z",
                "getV2Report",
                "PublishPlan",
                "publish-plan",
                "publish_plan",
                "plans.publish",
                "x1"
            })
    void GivenAnAsciiIdentifier_WhenRead_ThenItIsKeptAsGiven(String text) {
        assertThat(OperationName.of(text).value()).isEqualTo(text);
    }

    @Test
    void GivenAnIdentifierOfOneHundredCharacters_WhenRead_ThenItIsKept() {
        String hundred = "a".repeat(100);

        assertThat(OperationName.of(hundred).value()).hasSize(100);
    }

    @Test
    void GivenAnIdentifierOfOneHundredAndOneCharacters_WhenRead_ThenMalformedOperationName() {
        String hundredAndOne = "a".repeat(101);

        assertRefused(() -> OperationName.of(hundredAndOne), new MalformedOperationName());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "publish plan",
                " publishPlan",
                "publishPlan ",
                "publishPlan\n",
                "1plan",
                "-plan",
                "_plan",
                ".plan",
                "sign@in",
                "plans/publish",
                "פרסום",
                "café"
            })
    void GivenTextThatIsNotAnAsciiIdentifier_WhenRead_ThenMalformedOperationName(String text) {
        assertRefused(() -> OperationName.of(text), new MalformedOperationName());
    }
}
