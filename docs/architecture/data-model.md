# DispatchFlow — Data model

No data model exists in code today. `src/backend/core/src/main/resources/db/migration/` holds
only a `.gitkeep`, so the database has no table, and `core` has no JPA entity
([code-structure.md](code-structure.md#srcbackendcore)).

The decided design is the domain document, the tech stack and the ADRs: see
[domain layer design §2.3](domain_layer/domain_layer_design.md#23-rules-between-contexts) (each
module owns its tables) and [ADR-041](../ADRs/ADR-041-one-postgresql-schema-per-module-read-and-written-through-jooq.md).
The plan's sketch of the schema, a Proposed source until a later pull request retires it, is in the
[technical implementation plan §12](../init_impl_ref/technical_implementation_plan.md#12-database-schema).

This file will describe the data model as built once the first migration exists.
