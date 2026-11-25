package com.dispatchflow.web;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Caps every request body a converter reads at {@link #MAX_BODY_BYTES} (ADR-140 decision 9), since
 * neither Spring Boot nor Tomcat limits a JSON body. A body that declares a larger {@code
 * Content-Length} is refused before a byte is read; a chunked body is refused at the first byte
 * past the cap. Either answers 413 {@code BODY_TOO_LARGE} through {@code ContractErrorHandler}.
 * Multipart bodies have their own limits in {@code application.properties}.
 */
@ControllerAdvice
class BodySizeLimit extends RequestBodyAdviceAdapter {

    /**
     * 1 MiB: several times the body of a full plan at NFR-04 and NFR-05's 15 vehicles and 200
     * stops, the largest the contract will carry.
     */
    static final int MAX_BODY_BYTES = 1_048_576;

    @Override
    public boolean supports(
            MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (inputMessage.getHeaders().getContentLength() > MAX_BODY_BYTES) {
            throw new BodyTooLarge();
        }
        return new LimitedMessage(inputMessage);
    }

    /**
     * A request body over {@link #MAX_BODY_BYTES}. Its message names the limit only, never a byte
     * of the body.
     */
    static final class BodyTooLarge extends RuntimeException {

        BodyTooLarge() {
            super("The request body is over its limit of " + MAX_BODY_BYTES + " bytes");
        }
    }

    private record LimitedMessage(HttpInputMessage message) implements HttpInputMessage {

        @Override
        public InputStream getBody() throws IOException {
            return new LimitedStream(message.getBody());
        }

        @Override
        public HttpHeaders getHeaders() {
            return message.getHeaders();
        }
    }

    private static final class LimitedStream extends FilterInputStream {

        private static final int END_OF_STREAM = -1;

        private long read;

        LimitedStream(InputStream body) {
            super(body);
        }

        @Override
        public int read() throws IOException {
            int next = super.read();
            if (next != END_OF_STREAM) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        // FilterInputStream's skip goes straight to the body, so without this a skip would pass the
        // cap uncounted. It skips at most one byte past the cap, so a huge skip never drains the
        // rest of a body that is already refused.
        @Override
        public long skip(long bytes) throws IOException {
            long skipped = super.skip(Math.min(bytes, MAX_BODY_BYTES + 1L - read));
            if (skipped > 0) {
                count(skipped);
            }
            return skipped;
        }

        private void count(long bytes) {
            read += bytes;
            if (read > MAX_BODY_BYTES) {
                throw new BodyTooLarge();
            }
        }
    }
}
