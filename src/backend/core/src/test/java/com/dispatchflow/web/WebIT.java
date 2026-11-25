package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.IntegrationTest;
import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.SmartHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code web}'s pieces in the whole application, as it starts for real. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class WebIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String HEALTH = "/actuator/health";
    private static final String NO_SUCH_ROUTE = "/web-it/no-such-route";
    private static final String BOUNDARY = "web-it-boundary";
    private static final String PAGE_NOT_FOUND = "org.springframework.web.servlet.PageNotFound";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private RequestMappingHandlerAdapter adapter;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private CommitWatch commitWatch;

    @Autowired
    private JsonMapper sharedMapper;

    @Autowired
    private ApplicationContext context;

    @Test
    void GivenTheApplication_WhenItsHandlerAdapterIsRead_ThenItIsTheCallWrappersRetryingAdapter() {
        assertThat(adapter).isInstanceOf(CallWrapper.RetryingAdapter.class);
    }

    @Test
    void GivenTheApplication_WhenItsErrorControllerIsRead_ThenItIsTheErrorEndpointAndBootsStoodBack() {
        assertThat(context.getBean(ErrorController.class)).isInstanceOf(ErrorEndpoint.class);
    }

    @Test
    void GivenTheApplication_WhenItsTransactionManagerIsRead_ThenItReportsCommitsToTheCommitWatch() {
        assertThat(transactions).isInstanceOf(AbstractPlatformTransactionManager.class);
        assertThat(((AbstractPlatformTransactionManager) transactions).getTransactionExecutionListeners())
                .contains(commitWatch);
    }

    /**
     * Spring Data's web support adds a tolerant converter ahead of the served one, but it reads only
     * interfaces marked {@code @ProjectedPayload} and writes nothing, so a body of the contract's
     * reaches the strict one.
     */
    @Test
    void GivenTheApplication_WhenItsJsonConvertersAreRead_ThenTheServedOneIsStrictAndTheSharedMapperTolerant() {
        HttpMessageConverter<?> reader = adapter.getMessageConverters().stream()
                .filter(converter -> converter instanceof SmartHttpMessageConverter<?> smart
                        && smart.canRead(ResolvableType.forClass(ARequestBody.class), MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();

        assertThat(reader).isInstanceOfSatisfying(JacksonJsonHttpMessageConverter.class, served -> {
            assertThat(served.getMapper().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                    .isTrue();
            assertThat(served.getMapper().isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION))
                    .isTrue();
        });
        assertThat(sharedMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .isFalse();
        assertThat(sharedMapper.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION))
                .isFalse();
    }

    @Test
    void GivenAMultipartRequestToAnUnknownRoute_WhenItArrives_ThenItsBodyIsNotParsed() {
        AtomicInteger partsRead = new AtomicInteger();

        MvcTestResult result = mvc.perform(context -> multipartTo(context, partsRead));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NO_SUCH_OPERATION);
        assertThat(partsRead).hasValue(0);
    }

    @Test
    void GivenAMethodTheOperationDoesNotTake_WhenTheRequestArrives_Then405WithItsOwnCode() {
        MvcTestResult result = mvc.post().uri(HEALTH).exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.METHOD_NOT_ALLOWED);
        assertThat(result).headers().containsHeader(HttpHeaders.ALLOW);
    }

    /**
     * The 405 is the contract handler's INFO line alone: Spring's own WARN line, which quotes the
     * method the client chose, is turned off in {@code application.properties}.
     */
    @Test
    void GivenARefusal_WhenItIsLogged_ThenItsOneLineIsJsonCarryingTheTraceId(CapturedOutput output) {
        MvcTestResult result =
                mvc.post().uri(HEALTH).header("traceparent", TRACEPARENT).exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        List<String> traced =
                output.getOut().lines().filter(line -> line.contains(TRACE_ID)).toList();
        assertThat(traced).singleElement().satisfies(line -> {
            JsonNode logged = sharedMapper.readTree(line);
            assertThat(logged.path(Problems.TRACE_ID).asString()).isEqualTo(TRACE_ID);
            // Read as text, so the test doesn't depend on how ECS shapes its log.* fields.
            assertThat(line).contains("\"INFO\"").contains(ContractErrorHandler.class.getName());
        });
        assertThat(output.getOut()).doesNotContain(PAGE_NOT_FOUND);
    }

    @Test
    void GivenAFormBodyToAnyPath_WhenItArrives_ThenNoByteIsRead() {
        AtomicInteger bodyReads = new AtomicInteger();

        MvcTestResult result = mvc.perform(context -> formTo(context, bodyReads));

        assertThat(context.getBeansOfType(FormContentFilter.class)).isEmpty();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").asString().isEqualTo(WebCodes.NO_SUCH_OPERATION);
        assertThat(bodyReads).hasValue(0);
    }

    /**
     * A form PUT as a client sends one, the method whose body Spring's form filter would read
     * before routing; each read of its body is counted.
     */
    private static MockHttpServletRequest formTo(ServletContext context, AtomicInteger bodyReads) {
        MockHttpServletRequest request = new MockHttpServletRequest(context, "PUT", NO_SUCH_ROUTE) {
            @Override
            public ServletInputStream getInputStream() {
                bodyReads.incrementAndGet();
                return super.getInputStream();
            }

            @Override
            public BufferedReader getReader() throws UnsupportedEncodingException {
                bodyReads.incrementAndGet();
                return super.getReader();
            }
        };
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.setContent("name=Dana+Levi".getBytes(StandardCharsets.UTF_8));
        return request;
    }

    /** A request body as the generated models will be: a record. */
    record ARequestBody(String name) {}

    /**
     * A multipart POST as a client sends one, as a plain request, so Spring's multipart resolver
     * decides whether to parse it; each read of its parts is counted.
     */
    private static MockHttpServletRequest multipartTo(ServletContext context, AtomicInteger partsRead) {
        MockHttpServletRequest request = new MockHttpServletRequest(context, "POST", NO_SUCH_ROUTE) {
            @Override
            public Collection<Part> getParts() throws IOException, ServletException {
                partsRead.incrementAndGet();
                return super.getParts();
            }

            @Override
            public Part getPart(String name) throws IOException, ServletException {
                partsRead.incrementAndGet();
                return super.getPart(name);
            }
        };
        request.setContentType("multipart/form-data; boundary=" + BOUNDARY);
        request.setContent(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"proof\"; filename=\"p.png\""
                        + "\r\nContent-Type: image/png\r\n\r\nnot an image\r\n--" + BOUNDARY + "--\r\n")
                .getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
