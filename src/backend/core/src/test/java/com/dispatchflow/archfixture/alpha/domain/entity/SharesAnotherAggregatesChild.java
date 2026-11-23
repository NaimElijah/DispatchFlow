package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Rule: a root never relates to another aggregate's child, which MappedRoot owns. */
@Entity
@Table(schema = "alpha")
public class SharesAnotherAggregatesChild extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 16);

    @ManyToOne
    private @Nullable MappedChild borrowed;

    protected SharesAnotherAggregatesChild() {}

    @PostLoad
    void checkStored() {}
}
