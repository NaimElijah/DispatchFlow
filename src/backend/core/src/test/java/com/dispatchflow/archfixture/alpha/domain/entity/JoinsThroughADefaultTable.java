package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rule: a one-to-many with neither mappedBy nor a @JoinColumn gets a join table, which must name the schema. */
@Entity
@Table(schema = "alpha")
public class JoinsThroughADefaultTable {

    @Id
    private UUID id = new UUID(0, 18);

    @OneToMany
    private List<EntityKindHolder> holders = new ArrayList<>();

    protected JoinsThroughADefaultTable() {}
}
