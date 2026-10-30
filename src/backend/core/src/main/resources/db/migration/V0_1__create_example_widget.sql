-- Example only: the table behind the placeholder example context (com.dispatchflow.example).
-- Version 0.1 keeps V1 free for the team's first real migration. How to remove it is in
-- docs/architecture/code-structure.md.
-- The checks repeat the domain's rule, so no other writer can store a size the domain would reject.
CREATE TABLE example_widget (
    id     uuid    NOT NULL,
    width  integer NOT NULL,
    height integer NOT NULL,
    CONSTRAINT example_widget_pk PRIMARY KEY (id),
    CONSTRAINT example_widget_width_positive CHECK (width > 0),
    CONSTRAINT example_widget_height_positive CHECK (height > 0)
);
