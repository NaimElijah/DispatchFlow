/**
 * The ports and errors every module's application layer shares (DL §2.3 rule 5, TS §2.1 rule 3,
 * TS §6.4): the {@link com.dispatchflow.shared.application.Clock}, the {@link
 * com.dispatchflow.shared.application.IdGenerator}, the listeners' idempotency record {@link
 * com.dispatchflow.shared.application.ProcessedEvents} (DL §7.3), and the two errors a use case
 * raises outside the domain, {@link com.dispatchflow.shared.application.NotFound} (404) and {@link
 * com.dispatchflow.shared.application.StaleVersion} (412, or 503 after three retries for a call
 * that sent no version, ADR-121).
 *
 * <p>It sits under the kernel, so the ArchUnit purity rules check it with the kernel: it imports
 * only the JDK, JSpecify and the kernel. A change here needs the agreement of every module's owner
 * (DL §2.3 rule 5).
 */
@NullMarked
package com.dispatchflow.shared.application;

import org.jspecify.annotations.NullMarked;
