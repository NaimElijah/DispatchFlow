package com.dispatchflow.archfixture.alpha.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** Rule: only AggregateRoot declares a @Version. */
@Entity
@Table(schema = "alpha")
public class VersionedChild {

    @Id
    private UUID id = new UUID(0, 5);

    @Version
    private long version;

    protected VersionedChild() {}
}
