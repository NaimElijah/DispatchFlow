package com.dispatchflow.shared.adapter.in.web;

/**
 * Where the client API is served (ADR-006, ADR-043): the {@code servers} entry of {@code
 * contracts/client-api/v1}, and the package its interfaces are generated into at every build
 * (ADR-140 decision 2, ADR-160). The controllers build their {@code Location} headers from it, and
 * {@code web} serves the generated operations under it.
 */
public final class ClientApi {

    /** The major version the path carries, {@code v1}'s, as Spring's API versioning parses it. */
    public static final String VERSION = "1";

    /** The path every operation of the contract is served under. */
    public static final String BASE_PATH = "/api/v" + VERSION;

    /** The package of the generated {@code <Tag>Api} interfaces, one per tag. */
    public static final String GENERATED_API_PACKAGE = "com.dispatchflow.shared.adapter.in.web.api";

    private ClientApi() {}
}
