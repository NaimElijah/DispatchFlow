# DispatchFlow — Data model

No data model exists in code today. `src/backend/core/src/main/resources/db/migration/` holds
only a `.gitkeep`, so the database has no table, and `core` has no JPA entity
([code-structure.md](code-structure.md#srcbackendcore)).

The decided design is the domain document, the tech stack and the ADRs: see
[domain layer design §2.3](domain_layer/domain_layer_design.md#23-rules-between-contexts) (each
module owns its tables), [ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md)
(a schema per module) and [ADR-059](../ADRs/ADR-059-plan-revisions-are-stored-as-rows.md) (plan
revisions as rows). The tables and columns are written by the migrations that create them.

This file will describe the data model as built once the first migration exists.
