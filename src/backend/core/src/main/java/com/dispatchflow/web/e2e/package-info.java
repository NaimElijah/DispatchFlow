/**
 * The end-to-end tests' control endpoints (ADR-140), which exist only under the {@code e2e}
 * profile (ADR-130 decision 6). The first end-to-end test that needs one adds it, and reopens
 * ADR-140 decision 4 first, because the stand-in session refuses to boot beside the {@code e2e}
 * profile.
 */
@NullMarked
package com.dispatchflow.web.e2e;

import org.jspecify.annotations.NullMarked;
