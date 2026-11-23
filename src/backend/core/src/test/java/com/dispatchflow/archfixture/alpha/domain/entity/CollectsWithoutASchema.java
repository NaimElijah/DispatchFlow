package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rule: an element collection's table names its module's schema too. */
@Entity
@Table(schema = "alpha")
public class CollectsWithoutASchema {

    @Id
    private UUID id = new UUID(0, 17);

    @ElementCollection
    @OrderColumn
    private List<String> notes = new ArrayList<>();

    protected CollectsWithoutASchema() {}
}
