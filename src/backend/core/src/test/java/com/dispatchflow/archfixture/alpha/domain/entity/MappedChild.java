package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Allowed: a child entity, which may refer back to the root that holds it. */
@Entity
@Table(schema = "alpha")
public class MappedChild {

    @Id
    private UUID id = new UUID(0, 2);

    @ManyToOne
    private @Nullable MappedRoot root;

    protected MappedChild() {}
}
