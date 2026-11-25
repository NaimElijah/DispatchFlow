/**
 * Who the caller is, for every request (ADR-140): the stand-in session until sign-in is built
 * (#170), then Spring Security's filter chain with sign-in (#199), the 401 answer to a caller who
 * is not signed in, and the interceptor that checks each operation's capability (ADR-068, #171).
 */
@NullMarked
package com.dispatchflow.web.security;

import org.jspecify.annotations.NullMarked;
