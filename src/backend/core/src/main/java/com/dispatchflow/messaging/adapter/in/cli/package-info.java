/**
 * Recipient Messaging's server steps (ADR-130): {@code TurnMessagesOffAfterRestore}, from the
 * restore runbook, and {@code RehashOptOuts}, when the key rotates. It also holds Messaging's part
 * of the steps that span every module (ADR-131): the personal-data requests, the company's export
 * and deletion, and the pilot-day export, over Messaging's own personal columns.
 */
@NullMarked
package com.dispatchflow.messaging.adapter.in.cli;

import org.jspecify.annotations.NullMarked;
