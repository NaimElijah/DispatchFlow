package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.orders.domain.OrdersError;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.ServletInputStream;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class ContractErrorHandlerTest {

    private static final String PERSONAL_NAME = "Dana Levi";
    private static final String VALID_ORDER = "{\"name\":\"Dana\",\"items\":[\"box\"]}";
    private static final UUID PLAN_ID = UUID.fromString("0190a5e2-7c3b-7def-8a12-3456789abcde");
    private static final DeliveryDate DATE = DeliveryDate.of(LocalDate.of(2026, 3, 15));
    private static final String ORDERS = "/contract-error-handler-test/orders";
    private static final String BATCHES = "/contract-error-handler-test/batches";
    private static final Pattern LOGGED_ITEM = Pattern.compile("REQUIRED at (/lines/[0-9]+/name)");

    private final MockMvcTester mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new ContractController())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(
                    ServedJson.strict(JsonMapper.builder().build())))
            .setConversionService(refusingDays())
            .setControllerAdvice(
                    new ContractErrorHandler(),
                    new BodySizeLimit(),
                    new KernelErrorHandler(),
                    new UnexpectedErrorHandler())
            .addFilters(new TraceIdFilter())
            .build());

    /** A converter that refuses every day as a domain factory would, with a rule's refusal. */
    private static FormattingConversionService refusingDays() {
        FormattingConversionService conversion = new DefaultFormattingConversionService();
        conversion.addConverter(String.class, Day.class, text -> {
            throw new RuleViolation(new OrdersError.DateInPast(DATE));
        });
        return conversion;
    }

    private static String batchOf(int lines) {
        return IntStream.range(0, lines)
                .mapToObj(line -> "{\"name\":\"\"}")
                .collect(Collectors.joining(",", "{\"lines\":[", "]}"));
    }

    private static String loggedBy(CapturedOutput output, Class<?> logger) {
        return output.getOut()
                .lines()
                .filter(line -> line.contains(logger.getName()))
                .collect(Collectors.joining("\n"));
    }

    private MvcTestResult postOrder(String body) {
        return mvc.post()
                .uri(ORDERS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void isAnInvalidRequest(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(Problems.INVALID_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().hasSize(32);
    }

    @Test
    void GivenABodyWithTwoInvalidFields_WhenValidated_ThenBothAreListedAtOnce() throws Exception {
        MvcTestResult result = postOrder("{\"name\":\"" + PERSONAL_NAME + "\",\"items\":[]}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].pointer")
                .asArray()
                .containsExactlyInAnyOrder("/name", "/items");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[?(@.pointer == '/name')].code")
                .asArray()
                .containsExactly(WebCodes.WRONG_SIZE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[?(@.pointer == '/name')].params.max")
                .asArray()
                .containsExactly(4);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[?(@.pointer == '/items')].code")
                .asArray()
                .containsExactly(WebCodes.REQUIRED);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PERSONAL_NAME);
    }

    @Test
    void GivenAnUnknownProperty_WhenTheBodyIsRead_Then400NamingItsPointerAlone() {
        MvcTestResult result =
                postOrder("{\"name\":\"Dana\",\"items\":[\"box\"],\"address\":{\"city\":\"Haifa\",\"zip\":\"1\"}}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors.length()")
                .asNumber()
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.UNKNOWN_PROPERTY);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/address/zip");
    }

    @Test
    void GivenADuplicateKey_WhenTheBodyIsRead_Then400() {
        MvcTestResult result = postOrder("{\"name\":\"Dana\",\"name\":\"Noa\",\"items\":[\"box\"]}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.DUPLICATE_PROPERTY);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/name");
    }

    @Test
    void GivenAValueOfTheWrongType_WhenTheBodyIsRead_Then400NamingItsPointer() {
        MvcTestResult result = postOrder("{\"name\":\"Dana\",\"items\":\"box\"}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.WRONG_TYPE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/items");
    }

    @Test
    void GivenABodyThatIsNoJson_WhenItIsRead_Then400WithAnUnreadableBodyAtTheWholeBody() {
        MvcTestResult result = postOrder("{\"name\":");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.UNREADABLE_BODY);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEmpty();
    }

    @Test
    void GivenNoBody_WhenOneIsRequired_Then400WithAnUnreadableBody() {
        MvcTestResult result = mvc.post()
                .uri("/contract-error-handler-test/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.UNREADABLE_BODY);
    }

    @Test
    void GivenAMissingIfMatch_WhenTheRequestArrives_Then400NamingTheHeader() {
        MvcTestResult result = mvc.put()
                .uri("/contract-error-handler-test/plans/{id}", PLAN_ID)
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.MISSING_PARAMETER);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.name")
                .asString()
                .isEqualTo(HttpHeaders.IF_MATCH);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.in")
                .asString()
                .isEqualTo("header");
    }

    @Test
    void GivenAPathParameterOfTheWrongType_WhenTheRequestArrives_Then400NamingItsPlaceAndNotTheValue()
            throws Exception {
        String refused = "not-a-uuid";

        MvcTestResult result = mvc.put()
                .uri("/contract-error-handler-test/plans/{id}", refused)
                .header(HttpHeaders.IF_MATCH, "\"3\"")
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.WRONG_TYPE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.name")
                .asString()
                .isEqualTo("id");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.in")
                .asString()
                .isEqualTo("path");
        // The problem's instance is the request's own path, as the contract defines it; the item
        // that names the failure never repeats the value.
        assertThat(result).bodyJson().extractingPath("$.errors[0]").asMap().doesNotContainValue(refused);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].params")
                .asMap()
                .isEmpty();
    }

    @Test
    void GivenABodyOverItsLimit_WhenItIsRead_Then413WithItsOwnCode() {
        MvcTestResult result =
                postOrder("{\"name\":\"" + "a".repeat(BodySizeLimit.MAX_BODY_BYTES) + "\",\"items\":[\"box\"]}");

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.BODY_TOO_LARGE);
    }

    @Test
    void GivenAnUnknownPropertyWithALongNameInANestedObject_WhenTheBodyIsRead_ThenItsPointerIsCutToThatObject() {
        String longName = "p".repeat(Problems.MAX_POINTER_LENGTH * 2);

        MvcTestResult result =
                postOrder("{\"name\":\"Dana\",\"items\":[\"box\"],\"address\":{\"" + longName + "\":1}}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/address");
    }

    @Test
    void GivenAnUnknownPropertyWithASlashInItsName_WhenRead_ThenThePointerEscapesIt() {
        MvcTestResult result = postOrder("{\"name\":\"Dana\",\"items\":[\"box\"],\"a/b~c\":1}");

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/a~1b~0c");
    }

    @Test
    void GivenAPathValueWhoseConverterRefusesItWithARuleViolation_WhenTheRequestArrives_Then400NotTheKernels422() {
        MvcTestResult result = mvc.get()
                .uri("/contract-error-handler-test/days/{day}", "2026-03-15")
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.WRONG_TYPE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.name")
                .asString()
                .isEqualTo("day");
    }

    @Test
    void GivenAMissingPart_WhenTheRequestArrives_Then400RequiringItAtItsEscapedPointer() {
        MvcTestResult result = mvc.post()
                .uri("/contract-error-handler-test/proofs")
                .multipart()
                .part(new MockPart("note", "left at the door".getBytes(StandardCharsets.UTF_8)))
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.REQUIRED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEqualTo("/proof~1image");
    }

    @Test
    void GivenAnUnsatisfiedParameterCondition_WhenTheRequestArrives_Then400WithAnInvalidValueAtTheWholeRequest() {
        MvcTestResult result =
                mvc.get().uri("/contract-error-handler-test/reports").exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.INVALID_VALUE);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].pointer")
                .asString()
                .isEmpty();
    }

    @Test
    void GivenAStatusTheContractDoesNotList_WhenSpringAnswersIt_Then500LoggedAtErrorAsABug(CapturedOutput output) {
        MvcTestResult result =
                mvc.get().uri("/contract-error-handler-test/gone").exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNEXPECTED_ERROR);
        assertThat(loggedBy(output, ContractErrorHandler.class)).contains("ERROR");
    }

    @Test
    void GivenAQueryParameterBelowItsMinimum_WhenValidated_Then400NamingItInQuery() {
        MvcTestResult result = mvc.get()
                .uri("/contract-error-handler-test/stops")
                .queryParam("page", "0")
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].code")
                .asString()
                .isEqualTo(WebCodes.TOO_SMALL);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.name")
                .asString()
                .isEqualTo("page");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].parameter.in")
                .asString()
                .isEqualTo("query");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[0].params.value")
                .asNumber()
                .isEqualTo(1);
    }

    @Test
    void GivenAnInvalidFieldInAListElement_WhenValidated_ThenItsPointerHasTheIndex() {
        MvcTestResult result = mvc.post()
                .uri(BATCHES)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"\"}]}")
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].pointer")
                .asArray()
                .containsExactly("/lines/2/name");
    }

    @Test
    void GivenMoreFailingItemsThanTheCap_WhenValidated_ThenTheBodyAndTheLogListTheFirstHundred(CapturedOutput output)
            throws Exception {
        int lines = Problems.MAX_ITEMS + 50;

        MvcTestResult result = mvc.post()
                .uri(BATCHES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchOf(lines))
                .exchange();

        isAnInvalidRequest(result);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors.length()")
                .asNumber()
                .isEqualTo(Problems.MAX_ITEMS);
        List<String> answered = JsonPath.read(result.getResponse().getContentAsString(), "$.errors[*].pointer");
        String logged = loggedBy(output, ContractErrorHandler.class);
        assertThat(logged).contains("itemCount=\"" + lines + "\"");
        assertThat(LOGGED_ITEM.matcher(logged).results().map(item -> item.group(1)))
                .containsExactlyElementsOf(answered);
    }

    @Test
    void GivenATooLongValueInTheBody_WhenItIsRefused_ThenTheLogLineNeverCarriesIt(CapturedOutput output) {
        MvcTestResult result = postOrder("{\"name\":\"" + PERSONAL_NAME + "\",\"items\":[\"box\"]}");

        isAnInvalidRequest(result);
        assertThat(loggedBy(output, ContractErrorHandler.class)).contains("WRONG_SIZE");
        assertThat(output.getOut()).doesNotContain(PERSONAL_NAME);
    }

    @Test
    void GivenAChunkedBodyOverItsLimit_WhenItIsRead_Then413WithItsOwnCode() {
        AtomicLong bytesRead = new AtomicLong();
        byte[] body = ("{\"name\":\"" + "a".repeat(BodySizeLimit.MAX_BODY_BYTES) + "\",\"items\":[\"box\"]}")
                .getBytes(StandardCharsets.UTF_8);

        MvcTestResult result = mvc.perform(context -> {
            MockHttpServletRequest chunked = new MockHttpServletRequest(context, "POST", ORDERS) {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }

                @Override
                public ServletInputStream getInputStream() {
                    // DelegatingServletInputStream reads its source a byte at a time.
                    return new DelegatingServletInputStream(new ByteArrayInputStream(body) {
                        @Override
                        public synchronized int read() {
                            int read = super.read();
                            if (read >= 0) {
                                bytesRead.incrementAndGet();
                            }
                            return read;
                        }
                    });
                }
            };
            chunked.setContentType(MediaType.APPLICATION_JSON_VALUE);
            chunked.addHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
            return chunked;
        });

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.BODY_TOO_LARGE);
        assertThat(bytesRead.get()).isGreaterThan(BodySizeLimit.MAX_BODY_BYTES);
    }

    @Test
    void GivenAPathNoOperationHas_WhenTheRequestArrives_Then404NoSuchOperation() {
        MvcTestResult result =
                mvc.get().uri("/contract-error-handler-test/nowhere").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NO_SUCH_OPERATION);
    }

    @Test
    void GivenAMethodTheOperationDoesNotTake_WhenTheRequestArrives_Then405WithAllow() {
        MvcTestResult result =
                mvc.delete().uri("/contract-error-handler-test/orders").exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.METHOD_NOT_ALLOWED);
        assertThat(result).headers().containsHeader(HttpHeaders.ALLOW);
    }

    @Test
    void GivenAnAcceptTheOperationCannotAnswer_WhenTheRequestArrives_Then406() {
        MvcTestResult result = mvc.get()
                .uri("/contract-error-handler-test/plans")
                .accept(MediaType.APPLICATION_XML)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_ACCEPTABLE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NOT_ACCEPTABLE);
    }

    @Test
    void GivenAContentTypeTheOperationDoesNotRead_WhenTheRequestArrives_Then415WithAccept() {
        MvcTestResult result = mvc.post()
                .uri("/contract-error-handler-test/orders")
                .contentType(MediaType.TEXT_PLAIN)
                .content(VALID_ORDER)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).headers().containsHeader(HttpHeaders.ACCEPT);
    }

    @Test
    void GivenAValidRequest_WhenItArrives_ThenTheHandlerAnswers() {
        assertThat(postOrder(VALID_ORDER)).hasStatusOk();
    }

    @RestController
    static class ContractController {

        @PostMapping(ORDERS)
        String create(@Valid @RequestBody NewOrder order) {
            return order.name();
        }

        @PutMapping("/contract-error-handler-test/plans/{id}")
        String update(@PathVariable UUID id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
            return id.toString();
        }

        @GetMapping(value = "/contract-error-handler-test/plans", produces = MediaType.APPLICATION_JSON_VALUE)
        List<String> plans() {
            return List.of();
        }

        @GetMapping("/contract-error-handler-test/days/{day}")
        String day(@PathVariable Day day) {
            return day.toString();
        }

        @PostMapping("/contract-error-handler-test/proofs")
        String proof(@RequestPart("proof/image") MultipartFile image) {
            return image.getName();
        }

        @GetMapping(value = "/contract-error-handler-test/reports", params = "day")
        String report() {
            return "report";
        }

        @GetMapping("/contract-error-handler-test/gone")
        String gone() {
            throw new ResponseStatusException(HttpStatus.GONE);
        }

        @GetMapping("/contract-error-handler-test/stops")
        String stops(@RequestParam("page") @Min(1) int page) {
            return String.valueOf(page);
        }

        @PostMapping(BATCHES)
        String batch(@Valid @RequestBody Batch batch) {
            return String.valueOf(batch.lines().size());
        }
    }

    record NewOrder(@Size(max = 4) String name, @NotEmpty List<String> items, Address address) {}

    /** A path value that a converter makes, as a domain factory would. */
    record Day(LocalDate value) {}

    record Batch(List<@Valid Line> lines) {}

    record Line(@NotBlank String name) {}

    record Address(String city) {}
}
