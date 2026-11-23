package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Allowed: an entity that a join-table fixture relates to. */
@Entity
@Table(schema = "alpha")
public class EntityKindHolder {

    @Id
    private UUID id = new UUID(0, 19);

    protected EntityKindHolder() {}
}
