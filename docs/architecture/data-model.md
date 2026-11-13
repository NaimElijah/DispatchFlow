# DispatchFlow — Data model

No data model exists in code today. `src/backend/core/src/main/resources/db/migration/` holds
only a `.gitkeep`, so the database has no table, and `core` has no JPA entity
([code-structure.md](code-structure.md#srcbackendcore)).

The proposed design is in the
[technical implementation plan §12](../init_impl_ref/technical_implementation_plan.md#12-database-schema)
(the schema) and in
[domain layer design §2.3](domain_layer/domain_layer_design.md#23-rules-between-contexts) (each
module owns its tables). Both have status Proposed.

This file will describe the data model as built once the first migration exists.
