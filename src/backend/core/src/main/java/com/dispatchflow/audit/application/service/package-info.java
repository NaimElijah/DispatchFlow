/**
 * Audit's use cases: {@link com.dispatchflow.audit.application.service.AuditLogService}, which
 * implements the {@code AuditLog} API, and the retention job {@link
 * com.dispatchflow.audit.application.service.PurgeExpiredAuditRecords} (NFR-23, NFR-258, ADR-110).
 */
@NullMarked
package com.dispatchflow.audit.application.service;

import org.jspecify.annotations.NullMarked;
