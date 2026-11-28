package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.ClientApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the generated operations under {@code /api/v1}, the contract's {@code servers} entry, as
 * one literal prefix (ADR-160). The generated interfaces map each operation by its path within
 * the contract, such as {@code /plans}, so every controller that implements one gets the prefix,
 * and no other handler does: the error endpoint, the health probes and the {@code e2e} endpoints
 * keep their own paths.
 *
 * <p>The prefix is literal, not Spring Framework 7's API versioning, whose parser reads {@code 1},
 * {@code V1} and {@code v1.0} as {@code v1}, so those paths reached the controllers past the access
 * interceptor. Here the mapping, the interceptor and {@code NoStoreFilter} all match one decoded
 * path, and any other spelling is a path no operation has. A {@code v2} gets its own contract
 * folder, its own generated package and its own prefix beside this one.
 */
@Configuration(proxyBeanMethods = false)
class ClientApiPaths implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(ClientApi.BASE_PATH, ClientApiPaths::implementsAGeneratedInterface);
    }

    /**
     * Whether a handler class serves the contract: it implements one of the interfaces generated
     * from it, directly or through a superclass, as a proxy of the controller does.
     */
    static boolean implementsAGeneratedInterface(Class<?> handlerType) {
        return ClassUtils.getAllInterfacesForClassAsSet(handlerType).stream()
                .anyMatch(type -> type.getPackageName().equals(ClientApi.GENERATED_API_PACKAGE));
    }
}
