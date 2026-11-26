/**
 * Audit's persistence adapter (ADR-126, ADR-127): the JPA implementation of {@code AuditRecords}
 * over {@code audit.audit_record} and its named records. An {@code AuditRecord} is a Java record,
 * which Hibernate can't map as an entity, so it is stored through an {@code @Immutable} entity of
 * this package, keyed by a random UUID nothing outside the table sees (ADR-127 decision 6). The
 * package holds the auto-applied converters of Audit's single-column values.
 *
 * <p>The core only appends audit records, and reads none back, so no lookup is declared here.
 * Deleting expired records is ADR-041's database function, {@code audit.purge_expired_records()}
 * (migration {@code V12}, ADR-149), which the nightly retention run will call.
 */
@NullMarked
package com.dispatchflow.audit.adapter.out.persistence;

import org.jspecify.annotations.NullMarked;
