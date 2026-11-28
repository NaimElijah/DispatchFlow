package com.dispatchflow.web.security;

import com.dispatchflow.shared.adapter.in.web.ClientApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts the {@link AccessInterceptor} in front of every handler under {@code /api}, the root of
 * every version of the client API, so the operations under {@code /api/v1} are checked, and a path
 * under {@code /api} that no operation has answers a caller with no principal 401 before 404
 * (ADR-140, ADR-148, ADR-160).
 */
@Configuration(proxyBeanMethods = false)
class AccessInterceptorRegistration implements WebMvcConfigurer {

    /** Every path under the client API's root, whose {@code v1} is {@code servers}' {@code /api/v1}. */
    static final String CLIENT_API = ClientApi.ROOT + "/**";

    private final AccessInterceptor interceptor;

    AccessInterceptorRegistration(AccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(CLIENT_API);
    }
}
