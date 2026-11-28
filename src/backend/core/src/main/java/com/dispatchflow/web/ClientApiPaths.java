package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.ClientApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the generated operations under {@code /api/v1} through Spring Framework 7's API
 * versioning (TS §6, ADR-043, ADR-160). The generated interfaces map each operation by its path
 * within the contract, such as {@code /plans}, so every controller that implements one gets the
 * prefix {@code /api/{version}}, and the version is the path's second segment. Only {@code v1} is
 * supported, so a {@code v2} served beside it later is one more supported version and one more
 * generated package.
 *
 * <p>The version is read only from a path under {@code /api/}, so the error endpoint, the health
 * probes and the {@code e2e} endpoints keep their paths and need no version. Every other handler
 * keeps its own mapping too: the prefix goes only on a class that implements a generated
 * interface.
 */
@Configuration(proxyBeanMethods = false)
class ClientApiPaths implements WebMvcConfigurer {

    /** The prefix of every generated operation's path, its version a URI variable. */
    static final String VERSIONED_PREFIX = "/api/{version}";

    private static final String VERSIONED_ROOT = "/api/";
    // /api/v1/plans: "api" is segment 0, "v1" segment 1.
    private static final int VERSION_SEGMENT = 1;

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(
                        VERSION_SEGMENT,
                        path -> path.pathWithinApplication().value().startsWith(VERSIONED_ROOT))
                // A path outside /api/ carries no version, and must still find its handler.
                .setVersionRequired(false)
                .addSupportedVersions(ClientApi.VERSION);
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(VERSIONED_PREFIX, ClientApiPaths::implementsAGeneratedInterface);
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
