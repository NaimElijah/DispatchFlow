package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Rule: an entity lives in its own module's schema. */
@Entity
@Table(schema = "beta")
public class MappedInAnotherSchema {

    @Id
    private UUID id = new UUID(0, 11);

    protected MappedInAnotherSchema() {}
}
