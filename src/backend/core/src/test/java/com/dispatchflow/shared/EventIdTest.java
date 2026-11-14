package com.dispatchflow.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventIdTest {

    private static final UUID ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final EventId PUBLISHED = new EventId("PlanPublished", ID, Version.of(4), 1);

    @Test
    void GivenAnEventId_WhenWrittenAsText_ThenItIsTheCanonicalForm() {
        assertThat(PUBLISHED.toText()).isEqualTo("PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:4:1");
    }

    @Test
    void GivenTheCanonicalText_WhenParsed_ThenItIsTheSameId() {
        assertThat(EventId.parse(PUBLISHED.toText())).isEqualTo(PUBLISHED);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "PlanPublished",
                "PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:4",
                "PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:4:1:9",
                "PlanPublished:not-a-uuid:4:1",
                "PlanPublished:1-1-1-1-1:4:1",
                "PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:four:1",
                "PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:4:-1",
                "PlanPublished:0190a5e2-7c3b-7def-8a12-3456789abcde:0:0",
                ":0190a5e2-7c3b-7def-8a12-3456789abcde:4:1",
                "plan published:0190a5e2-7c3b-7def-8a12-3456789abcde:4:1"
            })
    void GivenMalformedText_WhenParsed_ThenStoredStateInvalid(String text) {
        assertThatThrownBy(() -> EventId.parse(text))
                .isInstanceOfSatisfying(
                        StoredStateInvalid.class,
                        invalid -> assertThat(invalid.subject()).isEqualTo("EventId"));
    }
}
