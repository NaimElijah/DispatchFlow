/**
 * Access's server steps (ADR-130), which a person runs at a prompt or from a runbook: {@code
 * CreateFirstAdmin}, {@code CreateAdminOnWrittenRequest}, {@code DeactivateOnWrittenRequest} and
 * {@code SecureAfterRestore}. It also holds Access's part of the steps that span every module
 * (ADR-131): the personal-data requests, the company's export and deletion, and the pilot-day
 * export, over Access's own personal columns.
 */
@NullMarked
package com.dispatchflow.access.adapter.in.cli;

import org.jspecify.annotations.NullMarked;
