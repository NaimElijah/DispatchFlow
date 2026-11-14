# DispatchFlow — Data model

No data model exists in code today. `src/backend/core/src/main/resources/db/migration/` holds
only a `.gitkeep`, so the database has no table, and `core` has no JPA entity
([code-structure.md](code-structure.md#srcbackendcore)).

The decided design is the domain document, the tech stack and the ADRs: see
[domain layer design §2.3](domain_layer/domain_layer_design.md#23-rules-between-contexts) (each
module owns its tables), [ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md)
(a schema per module) and [ADR-059](../ADRs/ADR-059-plan-revisions-are-stored-as-rows.md) (plan
revisions as rows). The persistence rules (jOOQ, optimistic locking, column types and the two
database roles) are in [tech stack §6.3](tech_stack.md#63-persistence), and the aggregates in
[domain layer design §4](domain_layer/domain_layer_design.md#4-aggregates-context-by-context). The
related ADRs are
[ADR-052](../ADRs/ADR-052-each-plan-stores-the-matrix-and-values-it-was-solved-with.md) (each plan
stores its matrix and values),
[ADR-057](../ADRs/ADR-057-concurrent-changes-to-a-plan-use-optimistic-locking.md) (optimistic
locking),
[ADR-075](../ADRs/ADR-075-retention-deletes-records-and-anonymizes-people-in-place.md) (retention)
and [ADR-088](../ADRs/ADR-088-israel-local-time-for-every-date-and-schedule.md) (Israel local time).
The tables and columns are written by the migrations that create them.

This file will describe the data model as built once the first migration exists.
