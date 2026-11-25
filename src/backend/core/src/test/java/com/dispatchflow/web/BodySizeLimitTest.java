package com.dispatchflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

class BodySizeLimitTest {

    private final BodySizeLimit limit = new BodySizeLimit();

    private HttpInputMessage read(byte[] body, HttpHeaders headers) throws IOException {
        return limit.beforeBodyRead(message(body, headers), null, String.class, JacksonJsonHttpMessageConverter.class);
    }

    private static HttpInputMessage message(byte[] body, HttpHeaders headers) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    @Test
    void GivenAChunkedBodyPastTheLimit_WhenItIsRead_ThenBodyTooLarge() throws IOException {
        HttpInputMessage limited = read(new byte[BodySizeLimit.MAX_BODY_BYTES + 1], new HttpHeaders());

        assertThatThrownBy(() -> limited.getBody().readAllBytes()).isInstanceOf(BodySizeLimit.BodyTooLarge.class);
    }

    @Test
    void GivenAChunkedBodyAtTheLimit_WhenItIsRead_ThenEveryByteIsRead() throws IOException {
        HttpInputMessage limited = read(new byte[BodySizeLimit.MAX_BODY_BYTES], new HttpHeaders());

        assertThat(limited.getBody().readAllBytes()).hasSize(BodySizeLimit.MAX_BODY_BYTES);
    }

    @Test
    void GivenAChunkedBodyPastTheLimitReadByteByByte_WhenItIsRead_ThenBodyTooLarge() throws IOException {
        InputStream body = read(new byte[BodySizeLimit.MAX_BODY_BYTES + 1], new HttpHeaders())
                .getBody();

        assertThatThrownBy(() -> {
                    int read;
                    do {
                        read = body.read();
                    } while (read != -1);
                })
                .isInstanceOf(BodySizeLimit.BodyTooLarge.class);
    }

    @Test
    void GivenAContentLengthExactlyAtTheLimit_WhenTheBodyIsRead_ThenEveryByteIsRead() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(BodySizeLimit.MAX_BODY_BYTES);

        HttpInputMessage limited = read(new byte[BodySizeLimit.MAX_BODY_BYTES], headers);

        assertThat(limited.getBody().readAllBytes()).hasSize(BodySizeLimit.MAX_BODY_BYTES);
    }

    @Test
    void GivenAContentLengthOverTheLimit_WhenTheBodyIsAboutToBeRead_ThenBodyTooLargeBeforeAnyByte() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(BodySizeLimit.MAX_BODY_BYTES + 1L);

        assertThatThrownBy(() -> read(new byte[0], headers)).isInstanceOf(BodySizeLimit.BodyTooLarge.class);
    }
}
