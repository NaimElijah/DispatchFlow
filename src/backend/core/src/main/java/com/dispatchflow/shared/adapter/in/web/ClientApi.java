package com.dispatchflow.shared.adapter.in.web;

/**
 * Where the client API is served (ADR-006, ADR-043, ADR-160): the {@code servers} entry of {@code
 * contracts/client-api/v1}, and the package its interfaces are generated into at every build
 * (ADR-140 decision 2). The controllers build their {@code Location} headers from it, {@code web}
 * serves the generated operations under it, and the access check and the no-store header guard
 * everything under {@link #ROOT}.
 */
public final class ClientApi {

    /**
     * The root of every version of the client API. The access interceptor and the no-store filter
     * guard all of it, so a path under it that no operation has is refused like one under {@code
     * /api/v1}, and a later version's prefix can't fall outside them.
     */
    public static final String ROOT = "/api";

    /** The path every operation of the contract's major version 1 is served under. */
    public static final String BASE_PATH = ROOT + "/v1";

    /** The package of the generated {@code <Tag>Api} interfaces, one per tag. */
    public static final String GENERATED_API_PACKAGE = "com.dispatchflow.shared.adapter.in.web.api";

    private ClientApi() {}
}
