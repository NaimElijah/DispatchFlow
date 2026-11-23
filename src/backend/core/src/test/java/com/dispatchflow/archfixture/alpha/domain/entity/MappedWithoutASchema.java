package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Rule: an entity names its module's schema, never the database's default one. */
@Entity
public class MappedWithoutASchema {

    @Id
    private UUID id = new UUID(0, 12);

    protected MappedWithoutASchema() {}
}
