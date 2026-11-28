package com.dispatchflow.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes the JSON converter of the API the core serves strict (ADR-140 decision 9): a request
 * body's property its schema doesn't declare, at any depth, and a key given twice in one object
 * both answer 400. Only the served converter is: the shared {@code JsonMapper} bean, and so the
 * optimizer client, keeps tolerating unknown properties, because an optional field added to a
 * response isn't a breaking change.
 *
 * <p>The same converter leaves an optional field with no value out of every answer, as the client
 * API's contract promises, rather than writing {@code null} (ADR-160). The generated models mark
 * such a field {@code @Nullable}, so the rule sits here, once for every answer, and not on each
 * model.
 */
@Configuration(proxyBeanMethods = false)
class ServedJson {

    /**
     * Replaces the server's JSON converter with a strict one built from the shared mapper, so every
     * setting {@code application.properties} gives the mapper still holds. It runs after Spring
     * Boot's own customizer, at order 0, which set the tolerant one.
     *
     * @param shared the application's {@code JsonMapper}
     * @return the customizer
     */
    @Bean
    @Order(1)
    ServerHttpMessageConvertersCustomizer servedJsonIsStrict(JsonMapper shared) {
        return builder -> builder.withJsonConverter(new JacksonJsonHttpMessageConverter(strict(shared)));
    }

    /**
     * A strict copy of a mapper. It is a builder, not a mapper, because the converter's builder
     * constructor adds the mix-in that writes a {@code ProblemDetail}'s properties at the top
     * level, and its mapper constructor doesn't.
     *
     * @param shared the mapper to copy, left unchanged
     * @return a builder of the copy, refusing unknown properties and duplicate keys, and leaving
     *     absent values out of what it writes
     */
    static JsonMapper.Builder strict(JsonMapper shared) {
        return shared.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .changeDefaultPropertyInclusion(
                        inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_ABSENT));
    }
}
