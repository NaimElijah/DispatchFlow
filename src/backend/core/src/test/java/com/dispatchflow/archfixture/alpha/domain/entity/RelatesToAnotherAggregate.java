package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Rule: a root refers to another aggregate only by its ID. */
@Entity
@Table(schema = "alpha")
public class RelatesToAnotherAggregate extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 3);

    @ManyToOne
    private @Nullable MappedRoot other;

    protected RelatesToAnotherAggregate() {}

    @PostLoad
    void checkStored() {}
}
