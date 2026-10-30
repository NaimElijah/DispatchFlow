/**
 * Example: an inbound adapter, the context's REST endpoints as {@code contracts/client-api/v1}
 * defines them. It checks the shape of a request, calls {@link
 * com.dispatchflow.example.api.ExampleApi}, and maps the answer to its own response types. It
 * imports only {@code api}, never {@code domain}, and holds no business rule.
 */
package com.dispatchflow.example.adapter.inbound.web;
