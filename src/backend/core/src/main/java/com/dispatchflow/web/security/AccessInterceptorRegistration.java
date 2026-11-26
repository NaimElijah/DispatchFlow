package com.dispatchflow.web.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the {@link AccessInterceptor} in front of every handler under {@code /api/v1} (ADR-140). */
@Configuration(proxyBeanMethods = false)
class AccessInterceptorRegistration implements WebMvcConfigurer {

    /** The paths of {@code client-api}, whose {@code servers} entry is {@code /api/v1}. */
    static final String CLIENT_API = "/api/v1/**";

    private final AccessInterceptor interceptor;

    AccessInterceptorRegistration(AccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(CLIENT_API);
    }
}
