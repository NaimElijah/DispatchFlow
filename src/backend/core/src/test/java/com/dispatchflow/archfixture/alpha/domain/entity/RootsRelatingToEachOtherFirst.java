package com.dispatchflow.archfixture.alpha.domain.entity;

import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Rule: two roots never relate, not even as the usual two-way mapping; between aggregates only IDs. */
@Entity
@Table(schema = "alpha")
public class RootsRelatingToEachOtherFirst extends AggregateRoot {

    @Id
    private UUID id = new UUID(0, 14);

    @OneToMany(mappedBy = "first")
    private List<RootsRelatingToEachOtherSecond> seconds = new ArrayList<>();

    protected RootsRelatingToEachOtherFirst() {}

    @PostLoad
    void checkStored() {}
}
