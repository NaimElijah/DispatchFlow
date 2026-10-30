package com.dispatchflow.example.adapter.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Example: an adapter test. It drives the context through HTTP, down to a real PostgreSQL and back. */
@IntegrationTest
class WidgetApiIT {

    private static final String WIDGETS = "/api/v1/examples/widgets";
    private static final String UNKNOWN_ID = "7d1f0a52-3b8e-4c1a-9a57-0e8b6f2c4d11";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void GivenPositiveDimensions_WhenCreated_ThenCreatedAtTheLocationOfTheReturnedWidget() throws Exception {
        MvcTestResult result = create("{\"width\": 10, \"height\": 20}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.width").asNumber().isEqualTo(10);
        assertThat(result).bodyJson().extractingPath("$.height").asNumber().isEqualTo(20);
        assertThat(idIn(result)).matches("[0-9a-f-]{36}");
        assertThat(result.getResponse().getHeader("Location")).endsWith(WIDGETS + "/" + idIn(result));
    }

    @Test
    void GivenACreatedWidget_WhenFetched_ThenItComesBackFromTheDatabase() throws Exception {
        MvcTestResult created = create("{\"width\": 30, \"height\": 40}");

        MvcTestResult fetched =
                mvc.get().uri(created.getResponse().getHeader("Location")).exchange();

        assertThat(fetched).hasStatusOk();
        assertThat(fetched).bodyJson().extractingPath("$.id").asString().isEqualTo(idIn(created));
        assertThat(fetched).bodyJson().extractingPath("$.width").asNumber().isEqualTo(30);
        assertThat(fetched).bodyJson().extractingPath("$.height").asNumber().isEqualTo(40);
    }

    @Test
    void GivenAZeroWidth_WhenCreated_ThenBadRequestAsAProblem() {
        MvcTestResult result = create("{\"width\": 0, \"height\": 20}");

        assertProblem(result, HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("width");
    }

    @Test
    void GivenAMissingHeight_WhenCreated_ThenBadRequestAsAProblem() {
        assertProblem(create("{\"width\": 10}"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void GivenAWidthWithAFraction_WhenCreated_ThenBadRequestInsteadOfTruncated() {
        assertProblem(create("{\"width\": 10.7, \"height\": 20}"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void GivenAWidthWrittenAsAString_WhenCreated_ThenBadRequestAsAProblem() {
        assertProblem(create("{\"width\": \"10\", \"height\": 20}"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void GivenABodyThatIsNotJson_WhenCreated_ThenBadRequestAsAProblem() {
        assertProblem(create("width=10&height=20"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void GivenAnIdThatIsNotAUuid_WhenFetched_ThenBadRequestAsAProblem() {
        assertProblem(mvc.get().uri(WIDGETS + "/not-a-uuid").exchange(), HttpStatus.BAD_REQUEST);
    }

    @Test
    void GivenAnUnknownId_WhenFetched_ThenNotFoundAsAProblem() {
        assertProblem(mvc.get().uri(WIDGETS + "/" + UNKNOWN_ID).exchange(), HttpStatus.NOT_FOUND);
    }

    private MvcTestResult create(String body) {
        return mvc.post()
                .uri(WIDGETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String idIn(MvcTestResult result) throws UnsupportedEncodingException {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status) {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(result).bodyJson().extractingPath("$.status").asNumber().isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.title").asString().isNotBlank();
    }
}
