package com.dispatchflow.archfixture.alpha.domain.mapping;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.UUID;

/** Allowed: Jakarta Persistence's annotations, and the enums they take as values (ADR-126). */
@Entity
@Table(schema = "alpha")
public class MappedWithJakartaPersistence {

    enum Kind {
        FIRST,
        SECOND
    }

    @Id
    private UUID id = new UUID(0, 1);

    @Enumerated(EnumType.STRING)
    private Kind kind = Kind.FIRST;

    protected MappedWithJakartaPersistence() {}

    @PostLoad
    void check() {}
}
