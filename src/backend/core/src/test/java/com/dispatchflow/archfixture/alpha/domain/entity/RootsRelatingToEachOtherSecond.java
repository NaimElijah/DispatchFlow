package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Rule: the other side of a two-way mapping between two roots. */
@Entity
@Table(schema = "alpha")
public class RootsRelatingToEachOtherSecond extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 15);

    @ManyToOne
    private @Nullable RootsRelatingToEachOtherFirst first;

    protected RootsRelatingToEachOtherSecond() {}

    @PostLoad
    void checkStored() {}
}
